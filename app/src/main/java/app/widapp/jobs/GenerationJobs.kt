package app.widapp.jobs

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.widapp.WidApp
import app.widapp.ai.ImageInput
import app.widapp.ai.NoProviderException
import app.widapp.core.JsonFileStore
import app.widapp.core.L
import app.widapp.spec.WidgetSize
import app.widapp.widget.WidgetBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable
data class GenJob(
    val id: String,
    /** create | surprise | refine | variant */
    val kind: String,
    val prompt: String = "",
    val size: String = "4x2",
    val style: String? = null,
    val designId: String? = null,
    val imagePath: String? = null,
    /** queued | running | done | failed | cancelled */
    val status: String = "queued",
    val resultDesignId: String? = null,
    val error: String? = null,
    val needsProvider: Boolean = false,
    val model: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    /** The UI has shown the outcome (navigated to it or displayed the error). */
    val seen: Boolean = false,
) {
    val active: Boolean get() = status == "queued" || status == "running"
}

@Serializable
data class JobList(val jobs: List<GenJob> = emptyList())

/**
 * Every AI generation runs as a persisted job executed by [GenerationWorker], so it survives
 * leaving the app, the screen being closed, and even the process being killed.
 */
class GenerationJobs(private val app: WidApp) {
    private val store = JsonFileStore(File(app.filesDir, "store/jobs.json"), JobList.serializer()) { JobList() }
    val flow: StateFlow<JobList> get() = store.flow
    private val imageDir get() = File(app.filesDir, "jobs").apply { mkdirs() }

    fun get(id: String?): GenJob? = store.value.jobs.firstOrNull { it.id == id }
    fun activeFor(designId: String): GenJob? = store.value.jobs.firstOrNull { it.designId == designId && it.active }

    fun enqueueCreate(prompt: String, size: WidgetSize, style: String?, image: ImageInput?) =
        enqueue(GenJob(newId(), "create", prompt = prompt, size = size.id, style = style), image)

    fun enqueueSurprise(size: WidgetSize) = enqueue(GenJob(newId(), "surprise", size = size.id), null)

    fun enqueueRefine(designId: String, instruction: String, image: ImageInput?) =
        enqueue(GenJob(newId(), "refine", prompt = instruction, designId = designId), image)

    fun enqueueVariant(designId: String) =
        enqueue(GenJob(newId(), "variant", prompt = L.t("Другой вариант", "Another take"), designId = designId), null)

    private fun newId() = UUID.randomUUID().toString().take(12)

    private fun enqueue(job: GenJob, image: ImageInput?): String {
        val withImage = if (image != null) {
            val f = File(imageDir, "${job.id}.b64").apply { writeText(image.mime + "\n" + image.base64) }
            job.copy(imagePath = f.path)
        } else job
        store.update { list -> list.copy(jobs = (listOf(withImage) + list.jobs).take(40)) }
        schedule(job.id)
        return job.id
    }

    fun schedule(id: String) {
        val req = OneTimeWorkRequestBuilder<GenerationWorker>()
            .setInputData(workDataOf(GenerationWorker.KEY_JOB to id))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(GenerationWorker.TAG)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, req)
    }

    fun workName(id: String) = "gen-$id"

    /** After a crash or update, put unfinished jobs back on the queue (KEEP makes this idempotent). */
    fun resumeUnfinished() = store.value.jobs.filter { it.active }.forEach { schedule(it.id) }

    fun cancel(id: String) {
        WorkManager.getInstance(app).cancelUniqueWork(workName(id))
        update(id) { if (it.active) it.copy(status = "cancelled", finishedAt = System.currentTimeMillis(), seen = true) else it }
        cleanup(id)
    }

    fun markSeen(id: String) = update(id) { it.copy(seen = true) }
    fun dismiss(id: String) = store.update { list -> list.copy(jobs = list.jobs.filterNot { it.id == id }) }

    fun retry(id: String) {
        update(id) { it.copy(status = "queued", error = null, needsProvider = false, seen = false, finishedAt = null) }
        schedule(id)
    }

    private fun update(id: String, f: (GenJob) -> GenJob) = store.update { list -> list.copy(jobs = list.jobs.map { if (it.id == id) f(it) else it }) }

    private fun cleanup(id: String) { File(imageDir, "$id.b64").delete() }

    private fun readImage(job: GenJob): ImageInput? = job.imagePath?.let { File(it) }?.takeIf { it.exists() }?.readText()?.let { t ->
        val nl = t.indexOf('\n')
        if (nl < 0) ImageInput(t) else ImageInput(t.substring(nl + 1), t.substring(0, nl))
    }

    /** Runs inside [GenerationWorker]. Never throws except for cancellation. */
    suspend fun run(id: String) {
        val job = get(id) ?: return
        if (!job.active) return
        update(id) { it.copy(status = "running") }
        val graph = app.graph
        try {
            val image = readImage(job)
            val size = WidgetSize.of(job.size)
            val designId: String
            val model: String
            when (job.kind) {
                "create" -> {
                    val r = app.generator.create(job.prompt, size, job.style, image)
                    designId = graph.designs.create(r.spec, job.prompt, r.model).id; model = r.model
                }
                "surprise" -> {
                    val r = app.generator.surprise(size)
                    designId = graph.designs.create(r.spec, "", r.model).id; model = r.model
                }
                "refine", "variant" -> {
                    val d = graph.designs.get(job.designId) ?: error(L.t("Виджет удалён", "The widget was deleted"))
                    val history = d.versions.take(d.current + 1).map { it.prompt }.filter { it.isNotBlank() }
                    val r = if (job.kind == "variant") app.generator.variant(d.spec, history)
                    else app.generator.refine(d.spec, history, job.prompt, image)
                    graph.designs.addVersion(d.id, r.spec, job.prompt, r.model)
                    designId = d.id; model = r.model
                }
                else -> error("Unknown job ${job.kind}")
            }
            update(id) { it.copy(status = "done", resultDesignId = designId, model = model, finishedAt = System.currentTimeMillis()) }
            cleanup(id)
            WidgetBinder.refreshAll(app)
            JobNotifications.done(app, get(id)!!, graph.designs.get(designId)?.name.orEmpty())
            // Fill live AI text right away so the first look is complete.
            graph.designs.get(designId)?.let { d ->
                val stale = app.liveContent.stale(d)
                if (stale.isNotEmpty()) app.liveContent.refresh(designId, stale)
            }
        } catch (e: CancellationException) {
            update(id) { if (it.active) it.copy(status = "cancelled", finishedAt = System.currentTimeMillis()) else it }
            throw e
        } catch (e: Exception) {
            update(id) {
                it.copy(
                    status = "failed", error = e.message ?: e.javaClass.simpleName,
                    needsProvider = e is NoProviderException, finishedAt = System.currentTimeMillis(),
                )
            }
            JobNotifications.failed(app, get(id)!!)
        }
    }
}
