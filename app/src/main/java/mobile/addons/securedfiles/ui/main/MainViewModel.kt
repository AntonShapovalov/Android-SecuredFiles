package mobile.addons.securedfiles.ui.main

import android.annotation.SuppressLint
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.Context
import mobile.addons.securedfiles.R
import mobile.addons.securedfiles.ext.*
import mobile.addons.securedfiles.file.FileManager
import mobile.addons.securedfiles.ui.abs.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subscriptions.CompositeSubscription
import java.io.File
import javax.inject.Inject

/**
 * Handles navigation drawer events from [MainActivity] and provides data for [MainFragment]
 */
class MainViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    @Inject lateinit var context: Context
    @Inject lateinit var fileManager: FileManager

    val title = MutableLiveData<String>()
    private var type = DOCUMENTS_TYPE
    val state = StateLiveData()
    var navId = 0 // to restore state from savedInstanceState

    private val subscriptions: CompositeSubscription = CompositeSubscription()

    fun setInitialState(navId: Int) {
        if (state.value != StateIdle) return
        onNavItemSelected(navId)
        val s = fileManager.queueSubject.filter { it == type }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ loadFiles() }, { it.printStackTrace() })
        subscriptions.add(s)
    }

    /**
     * Navigation Drawer item selected in [MainActivity]
     */
    fun onNavItemSelected(navId: Int) {
        val titleId: Int
        when (navId) {
            R.id.nav_music -> {
                titleId = R.string.nav_title_music
                type = AUDIO_TYPE
            }
            R.id.nav_pictures -> {
                titleId = R.string.nav_title_pic
                type = IMAGE_TYPE
            }
            R.id.nav_videos -> {
                titleId = R.string.nav_title_video
                type = VIDEO_TYPE
            }
            else -> {
                titleId = R.string.nav_title_doc
                type = DOCUMENTS_TYPE
            }
        }
        title.value = context.getString(titleId)
        this.navId = navId
        loadFiles()
    }

    /**
     * Delete file from internal directory and reload list
     */
    fun deleteFile(file: File) {
        val s = Observable.fromCallable { file.delete() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { state.value = StateProgress }
                .subscribe({ loadFiles() }, { state.value = StateError(it) })
        subscriptions.add(s)
    }

    /**
     * Load files from selected directory and notify [MainFragment]
     * If file is very large (video) - it can be in both lists, so we filter internal items while file is in coping
     */
    private fun loadFiles() {
        val queueItems = Observable.fromCallable { fileManager.getQueueItems(type) }
                .doOnNext { logItems("queueItems", it) }
        val internalItems = Observable.fromCallable { context.getInternalFiles(type).map { InternalItem(it, type) } }
                .doOnNext { logItems("internalItems", it) }
        val s = Observable.zip(queueItems, internalItems) { q, i -> deleteDuplicates(q, i) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { state.value = StateProgress }
                .subscribe({ state.value = InternalFilesLoaded(it) }, { state.value = StateError(it) })
        subscriptions.add(s)
    }

    private fun logItems(title: String, items: List<InternalItem>) {
        log("$title -->")
        items.forEach { log("file=${it.file.name},${it.loadState.javaClass.simpleName}") }
    }

    private fun deleteDuplicates(q: List<InternalItem>, i: List<InternalItem>): List<InternalItem> {
        val qNames = q.map { it.file.name }.toHashSet()
        val iFiltered = i.filter { !qNames.contains(it.file.name) }
        return q.toMutableList().apply { addAll(iFiltered) }.toList()
    }

    override fun onCleared() {
        super.onCleared()
        log("MainViewModel:onCleared")
        subscriptions.unsubscribe()
    }

}

class InternalItem(override val file: File, val type: String, var loadState: ItemLoadState = ItemLoadSuccess) : FileItem

sealed class ItemLoadState
object ItemLoadSuccess : ItemLoadState()
object ItemLoadProgress : ItemLoadState()
data class ItemLoadError(val message: String) : ItemLoadState()