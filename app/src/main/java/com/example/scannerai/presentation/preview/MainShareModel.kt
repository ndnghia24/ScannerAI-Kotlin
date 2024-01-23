package com.example.scannerai.presentation.preview

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerai.domain.hit_test.HitTestResult
import com.example.scannerai.domain.utils.tree.Tree
import com.example.scannerai.domain.utils.tree.TreeNode
import com.example.scannerai.presentation.LabelObject
import com.example.scannerai.presentation.confirmer.ConfirmFragment
import com.example.scannerai.presentation.preview.state.PathState
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.ar.arcore.ArFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainShareModel @Inject constructor(
    private val tree: Tree,
): ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var _confirmationObject = MutableStateFlow<LabelObject?>(null)
    val confirmationObject = _confirmationObject.asStateFlow()

    private var _pathState = MutableStateFlow(PathState())
    val pathState = _pathState.asStateFlow()

    private var _frame = MutableStateFlow<ArFrame?>(null)
    val frame = _frame.asStateFlow()

    private var _mainUiEvents = MutableSharedFlow<MainUiEvent>()
    val mainUiEvents = _mainUiEvents.asSharedFlow()

    private var _selectedNode = MutableStateFlow<TreeNode?>(null)
    val selectedNode = _selectedNode.asStateFlow()

    private var _linkPlacementMode = MutableStateFlow(false)
    val linkPlacementMode = _linkPlacementMode.asStateFlow()

    init {
        preload()
    }

    fun onEvent(event: MainEvent) {
        when (event){
            is MainEvent.NewFrame -> {
                viewModelScope.launch {
                    _frame.emit(event.frame)
                }
            }
            is MainEvent.NewConfirmationObject -> {
                _confirmationObject.update { event.confObject }
            }
            is MainEvent.AcceptConfObject -> {
                when (event.confirmType) {
                    ConfirmFragment.CONFIRM_INITIALIZE -> {
                        viewModelScope.launch {
                            _confirmationObject.value?.let {
                                initialize(
                                    it.label,
                                    it.pos.position,
                                    it.pos.orientation
                                )
                                _confirmationObject.update { null }
                            }
                        }
                    }
                    ConfirmFragment.CONFIRM_ENTRY -> {
                        viewModelScope.launch {
                            _confirmationObject.value?.let {
                                createNode(
                                    number = it.label,
                                    position = it.pos.position,
                                    orientation = it.pos.orientation
                                )
                                _confirmationObject.update { null }
                            }
                        }
                    }
                }
            }
            is MainEvent.RejectConfObject -> {
                viewModelScope.launch {
                    _confirmationObject.update { null }
                }
            }
            is MainEvent.NewSelectedNode -> {
                viewModelScope.launch {
                    _selectedNode.update { event.node }
                }
            }
            is MainEvent.CreateNode -> {
                viewModelScope.launch {
                    createNode(
                        number = event.number,
                        position = event.position,
                        orientation = event.orientation,
                        hitTestResult = event.hitTestResult
                    )
                }
            }
            is MainEvent.DeleteNode -> {
                viewModelScope.launch {
                    removeNode(event.node)
                }
            }
        }
    }

    private suspend fun createNode(
        number: String? = null,
        position: Float3? = null,
        orientation: Quaternion? = null,
        hitTestResult: HitTestResult? = null,
    ) {
        if (position == null && hitTestResult == null){
            throw Exception("No position was provided")
        }

        val treeNode = tree.addNode(
            position ?: hitTestResult!!.orientatedPosition.position,
            number,
            orientation
        )

        treeNode.let {
            if (number != null){
                _mainUiEvents.emit(MainUiEvent.EntryCreated)
            }
            _mainUiEvents.emit(
                MainUiEvent.NodeCreated(
                    treeNode,
                    hitTestResult?.hitResult?.createAnchor()
                )
            )
        }
    }

    private suspend fun removeNode(node: TreeNode){
        tree.removeNode(node)
        _mainUiEvents.emit(MainUiEvent.NodeDeleted(node))
        if (node == selectedNode.value) {_selectedNode.update { null }}
        if (node == pathState.value.endEntry) {_pathState.update { it.copy(endEntry = null, path = null) }}
        else if (node == pathState.value.startEntry) {_pathState.update { it.copy(startEntry = null, path = null) }}
    }

    private suspend fun initialize(entryNumber: String, position: Float3, newOrientation: Quaternion): Boolean {
        var result: Result<Unit?>
        withContext(Dispatchers.IO) {
            result = tree.initialize(entryNumber, position, newOrientation)
        }
        if (result.isFailure){
            _mainUiEvents.emit(
                MainUiEvent.InitFailed(
                    result.exceptionOrNull() as java.lang.Exception?
                )
            )
            return false
        }
        _mainUiEvents.emit(MainUiEvent.InitSuccess)
        _pathState.update { PathState() }
        return true
    }

    private fun preload(){
        viewModelScope.launch {
            tree.preload()
        }
    }
}
