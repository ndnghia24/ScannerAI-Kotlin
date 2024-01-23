package com.example.scannerai.domain.utils.tree

import android.util.Log
import com.example.scannerai.domain.use_cases.convert
import com.example.scannerai.domain.use_cases.inverted
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class Tree @Inject constructor(
    //private val repository: GraphRepository
) {
    private val _entryPoints: MutableMap<String, TreeNode.Entry> = mutableMapOf()
    private val _allPoints: MutableMap<Int, TreeNode> = mutableMapOf()
    private val _links: MutableMap<Int, MutableList<Int>> = mutableMapOf()
    //Nodes without links. Needed for near nodes calculation in TreeDiffUtils
    private val _freeNodes: MutableList<Int> = mutableListOf()
    private val _regions: MutableMap<Int, Int> = mutableMapOf()

    private val _translocatedPoints: MutableMap<TreeNode, Boolean> = mutableMapOf()

    private var availableId = 0
    private var availableRegion = 0

    var initialized = false
        private set
    var preloaded = false
        private set

    var translocation = Float3(0f, 0f, 0f)
        private set

    var pivotPosition = Float3(0f, 0f, 0f)
        private set

    var rotation = Float3(0f, 0f, 0f).toQuaternion()
        private set

    suspend fun preload() = withContext(Dispatchers.IO) {
    }

    suspend fun initialize(entryNumber: String, position: Float3, newRotation: Quaternion): Result<Unit?> {
        initialized = false
        if (_entryPoints.isEmpty()) {
            clearTree()
            initialized = true
            return Result.success(null)
        }
        else {
            val entry = _entryPoints[entryNumber]
                ?: return Result.failure(
                    exception = WrongEntryException(_entryPoints.keys)
                )

            pivotPosition = entry.position
            translocation = entry.position - position
            rotation = entry.forwardVector.convert(newRotation.inverted()) * -1f
            rotation.w *= -1f

            initialized = true
            return Result.success(null)
        }
    }

    suspend fun addNode(
        position: Float3,
        number: String? = null,
        forwardVector: Quaternion? = null
    ): TreeNode {
        if (!initialized){
            throw Exception("Tree isnt initialized")
        }
        if (_allPoints.values.find { it.position == position } != null) {
            throw Exception("Position already taken")
        }
        val newNode: TreeNode
        if (number == null){
            newNode = TreeNode.Path(
                availableId,
                position
            )
        }
        else {
            if (_entryPoints[number] != null) {
                throw Exception("Entry point already exists")
            }
            if (forwardVector == null){
                throw Exception("Null forward vector")
            }
            newNode = TreeNode.Entry(
                number,
                forwardVector,
                availableId,
                position
            )
            _entryPoints[newNode.number] = newNode
        }

        _allPoints[newNode.id] = newNode
        _translocatedPoints[newNode] = true
        _freeNodes.add(newNode.id)
        availableId++
        //repository.insertNodes(listOf(newNode), translocation, rotation, pivotPosition)
        return newNode
    }

    suspend fun removeNode(
        node: TreeNode
    ) {
        if (!initialized){
            throw Exception("Tree isnt initialized")
        }

        _translocatedPoints.remove(node)
        _allPoints.remove(node.id)
        _freeNodes.remove(node.id)
        _regions.remove(node.id)
        if (node is TreeNode.Entry) {
            _entryPoints.remove(node.number)
        }
        //repository.deleteNodes(listOf(node))
    }

    private suspend fun clearTree(){
        Log.d(TAG, "Tree cleared")
        _links.clear()
        _allPoints.clear()
        _entryPoints.clear()
        _translocatedPoints.clear()
        _freeNodes.clear()
        availableId = 0
        availableRegion = 0
        translocation = Float3(0f, 0f, 0f)
        rotation = Float3(0f, 0f, 0f).toQuaternion()
        pivotPosition = Float3(0f, 0f, 0f)
        //repository.clearNodes()
    }

    companion object {
        const val TAG = "TREE"
    }

}