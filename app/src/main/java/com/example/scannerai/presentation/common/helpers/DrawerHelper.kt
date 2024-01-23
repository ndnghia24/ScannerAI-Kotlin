package com.example.scannerai.presentation.common.helpers

import android.graphics.Color
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.scannerai.R
import com.example.scannerai.data.App
import com.example.scannerai.domain.hit_test.OrientatedPosition
import com.example.scannerai.domain.utils.tree.TreeNode
import com.example.scannerai.presentation.preview.PreviewFragment
import com.google.ar.core.Anchor
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.uchuhimo.collections.MutableBiMap
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.ar.scene.destroy
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.math.toNewQuaternion
import io.github.sceneview.math.toVector3
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch


class DrawerHelper(
    private val fragment: PreviewFragment,
    ) {
    //private var labelScale = Scale(0.15f, 0.075f, 0.15f)
    private var labelScale = Scale(1f, 0.8f, 0f)
    private var arrowScale = Scale(0.5f, 0.5f, 0.5f)
    private var pathScale = Scale(0.1f)
    private var entryScale = Scale(0.05f)
    //private var entryScale = Scale(0.3f)
    private var selectionPathScale = Scale(0.2f)
    private var selectionEntryScale = Scale(0.1f)
    private val pathModel = "models/cylinder.glb"
    private val entryModel = "models/box.glb"
    private val selectionModel = "models/cone.glb"
    private var labelAnimationDelay = 2L
    private var arrowAnimationDelay = 2L
    private var labelAnimationPart = 10
    private var arrowAnimationPart = 15
    private val bias = 0.15f

    private val animationJobs = mutableMapOf<ArNode, Job>()

    suspend fun drawNode(
        treeNode: TreeNode,
        surfaceView: ArSceneView,
        anchor: Anchor? = null
    ): ArNode {
        return when (treeNode){
            is TreeNode.Path -> {
                drawPath(treeNode, surfaceView, anchor)
            }
            is TreeNode.Entry -> {
                drawEntry(treeNode, surfaceView, anchor)
            }
        }

    }

    suspend fun removeLink(
        pair: Pair<ArNode, ArNode>,
        modelsToLinkModels: MutableBiMap<Pair<ArNode, ArNode>, ArNode>
    ) {
        modelsToLinkModels[pair]?.destroy()
        modelsToLinkModels.remove(pair)
    }

    suspend fun removeNode(
        treeNode: TreeNode,
        modelsToLinkModels: MutableBiMap<Pair<ArNode, ArNode>, ArNode>,
        treeNodesToModels: MutableBiMap<TreeNode, ArNode>,
    ){
        treeNodesToModels[treeNode]?.let { node ->
            modelsToLinkModels.keys
                .filter { pair ->
                    pair.first == node || pair.second == node
                }
                .forEach { pair ->
                    removeLink(pair, modelsToLinkModels)
                }
            treeNodesToModels.remove(treeNode)
            node.destroy()
            node.anchor?.destroy()
        }
    }

    suspend fun drawSelection(
        treeNode: TreeNode,
        surfaceView: ArSceneView,
    ): ArNode = when (treeNode) {
        is TreeNode.Entry -> {
            drawArNode(selectionModel, selectionEntryScale, treeNode.position, treeNode.forwardVector, surfaceView, null)
        }
        is TreeNode.Path -> {
            drawArNode(selectionModel, selectionPathScale, treeNode.position, null, surfaceView, null)
        }
    }

    private suspend fun drawPath(
        treeNode: TreeNode.Path,
        surfaceView: ArSceneView,
        anchor: Anchor? = null
    ): ArNode = drawArNode(pathModel, pathScale, treeNode.position, null, surfaceView, anchor)

    private suspend fun drawArNode(
        model: String,
        scale: Scale,
        position: Float3,
        orientation: dev.romainguy.kotlin.math.Quaternion?,
        surfaceView: ArSceneView,
        anchor: Anchor?
    ): ArNode {
        val modelNode = ArModelNode().apply {
            loadModel(
                context = fragment.requireContext(),
                lifecycle = fragment.lifecycle,
                glbFileLocation = model,
            )
            this.position = position
            orientation?.let {
                quaternion = it
            }
            modelScale = scale
            followHitPosition = false
            if (anchor != null){
                this.anchor = anchor
            }
            else {
                this.anchor = createAnchor()
            }
            this.model?.let {
                it.isShadowCaster = false
                it.isShadowReceiver = false
            }
        }

        surfaceView.addChild(modelNode)

        return modelNode
    }

    private suspend fun drawEntry(
        treeNode: TreeNode.Entry,
        surfaceView: ArSceneView,
        anchor: Anchor? = null
    ): ArNode {
        return placeLabel(
            treeNode.number,
            OrientatedPosition(treeNode.position, treeNode.forwardVector),
            surfaceView
        ).apply {
            if (App.isAdmin) {
                addChild(
                    drawArNode(
                        model = entryModel,
                        scale = entryScale,
                        position = Position(0f, -bias, 0f),
                        orientation = dev.romainguy.kotlin.math.Quaternion(),
                        surfaceView = surfaceView,
                        anchor = anchor
                    )
                )
            }
        }

    }


    suspend fun placeLabel(
        label: String,
        pos: OrientatedPosition,
        surfaceView: ArSceneView,
        anchor: Anchor? = null
    ): ArNode = placeRend(
        label = label,
        pos = pos,
        surfaceView = surfaceView,
        scale = labelScale,
        anchor = anchor
    )

    private suspend fun placeRend(
        label: String? = null,
        pos: OrientatedPosition,
        surfaceView: ArSceneView,
        scale: Scale,
        anchor: Anchor? = null
    ): ArNode {
        var node: ArNode? = null
        ViewRenderable.builder()
            .setView(fragment.requireContext(), R.layout.ar_text_sign)
            .setSizer { Vector3(0f, 0f, 0f) }
            .setVerticalAlignment(ViewRenderable.VerticalAlignment.CENTER)
            .setHorizontalAlignment(ViewRenderable.HorizontalAlignment.CENTER)
            .build(fragment.lifecycle)
            .thenAccept { renderable: ViewRenderable ->
                renderable.let {
                    it.isShadowCaster = false
                    it.isShadowReceiver = false
                }
                if (label != null) {
                    val cardView = renderable.view as CardView
                    val textView: TextView = cardView.findViewById(R.id.signTextView)
                    textView.text = label
                }
                val textNode = ArModelNode().apply {
                    followHitPosition = false
                    setModel(
                        renderable = renderable
                    )
                    position = Position(pos.position.x, pos.position.y, pos.position.z)
                    quaternion = pos.orientation
                    if (anchor != null){
                        this.anchor = anchor
                    }
                    else {
                        this.anchor = this.createAnchor()
                    }
                }
                surfaceView.addChild(textNode)
                node = textNode
                textNode.animateViewAppear(
                    scale,
                    if (label != null) labelAnimationDelay else arrowAnimationDelay,
                    if (label != null) labelAnimationPart else arrowAnimationPart
                )
            }
            .await()

        return node!!
    }

    fun removeNode(node: ArNode) {
        node.destroy()
        node.anchor?.destroy()
        animationJobs[node]?.cancel()
    }

    fun removeArrowWithAnim(node: ArNode) {
        node.model as ViewRenderable? ?: throw Exception("No view renderable")
        node.animateViewDisappear(arrowScale, arrowAnimationDelay, arrowAnimationPart)
    }

    fun removeLabelWithAnim(node: ArNode) {
        node.model as ViewRenderable? ?: throw Exception("No view renderable")
        node.animateViewDisappear(arrowScale, labelAnimationDelay, labelAnimationPart)
    }

    fun drawLine(
        from: ArNode,
        to: ArNode,
        modelsToLinkModels: MutableBiMap<Pair<ArNode, ArNode>, ArNode>,
        surfaceView: ArSceneView
    ){

        val fromVector = from.position.toVector3()
        val toVector = to.position.toVector3()

        // Compute a line's length
        val lineLength = Vector3.subtract(fromVector, toVector).length()

        // Prepare a color
        val colorOrange = com.google.ar.sceneform.rendering.Color(Color.parseColor("#ffffff"))

        // 1. make a material by the color
        MaterialFactory.makeOpaqueWithColor(fragment.requireContext(), fragment.lifecycle, colorOrange)
            .thenAccept { material: Material? ->
                // 2. make a model by the material
                val model = ShapeFactory.makeCylinder(
                    fragment.lifecycle,
                    0.01f, lineLength,
                    Vector3(0f, lineLength / 2, 0f), material
                )

                model.isShadowCaster = false
                model.isShadowReceiver = false

                // 3. make node
                val node = ArNode()
                node.setModel(model)
                node.parent = from
                surfaceView.addChild(node)

                // 4. set rotation
                val difference = Vector3.subtract(toVector, fromVector)
                val directionFromTopToBottom = difference.normalized()
                val rotationFromAToB: Quaternion =
                    Quaternion.lookRotation(
                        directionFromTopToBottom,
                        Vector3.up()
                    )

                val rotation = Quaternion.multiply(
                    rotationFromAToB,
                    Quaternion.axisAngle(Vector3(1.0f, 0.0f, 0.0f), 270f)
                )

                node.modelQuaternion = rotation.toNewQuaternion()
                node.position = from.position

                modelsToLinkModels[Pair(from, to)] = node
            }
    }

    private fun ArNode.animateViewAppear(targetScale: Scale, delay: Long, part: Int) {
        animateView(true, targetScale, delay, part, end = null)
    }

    private fun ArNode.animateViewDisappear(targetScale: Scale, delay: Long, part: Int) {
        animateView(false, targetScale, delay, part) {
            removeNode(it)
        }
    }

    private fun ArNode.animateView(appear: Boolean, targetScale: Scale, delay: Long, part: Int, end: ((ArNode) -> Unit)?) {
        val renderable = this.model as ViewRenderable? ?: throw Exception("No view renderable")
        var size = renderable.sizer.getSize(renderable.view)
        val xPart = targetScale.x/part
        val yPart = targetScale.y/part
        val zPart = targetScale.z/part
        animationJobs[this]?.cancel()
        animationJobs[this] = fragment.viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                if (size.x >= targetScale.toVector3().x && appear) {
                    break
                }
                else if(size.x <= 0 && !appear){
                    break
                }
                renderable.sizer = ViewSizer {
                    if (appear)
                        size.addConst(xPart, yPart, zPart)
                    else
                        size.addConst(xPart, yPart, zPart, -1)

                }
                delay(delay)
                size = renderable.sizer.getSize(renderable.view)
            }
            if (end != null) {
                end(this@animateView)
            }
        }
    }

    suspend fun joinAnimation(node: ArNode) {
        animationJobs[node]?.join()
    }

    private fun Vector3.addConst(xValue: Float, yValue: Float, zValue: Float, modifier: Int = 1): Vector3 {
        return Vector3(
            x + xValue * modifier,
            y + yValue * modifier,
            z + zValue * modifier
        )
    }
}