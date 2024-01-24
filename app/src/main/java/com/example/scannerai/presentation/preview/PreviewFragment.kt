package com.example.scannerai.presentation.preview

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.scannerai.R
import com.example.scannerai.data.App
import com.example.scannerai.databinding.FragmentPreviewBinding
import com.example.scannerai.domain.utils.tree.TreeNode
import com.example.scannerai.domain.utils.tree.WrongEntryException
import com.example.scannerai.presentation.LabelObject
import com.example.scannerai.presentation.common.helpers.DrawerHelper
import com.example.scannerai.presentation.preview.nodes_adapters.TreeAdapter
import com.example.scannerai.presentation.preview.state.PathState
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.*
import com.uchuhimo.collections.MutableBiMap
import com.uchuhimo.collections.mutableBiMapOf
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.node.ArNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PreviewFragment : Fragment() {
    private val mainModel: MainShareModel by activityViewModels()

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private val drawerHelper = DrawerHelper(this);

    private var endPlacingJob: Job? = null
    private var startPlacingJob: Job? = null
    private var wayBuildingJob: Job? = null
    private var currentPathState: PathState? = null
    private var lastPositionTime = 0L
    private lateinit var treeAdapter: TreeAdapter

    private var lastConfObject: LabelObject? = null
    private var confObjectJob: Job? = null
    private val treeNodesToModels: MutableBiMap<TreeNode, ArNode> = mutableBiMapOf()
    private var selectionJob: Job? = null
    private var selectionNode: ArNode? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("PreviewFragment", "onCreateView")
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.sceneView.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        binding.sceneView.onPause(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        treeAdapter = TreeAdapter(
            drawerHelper,
            binding.sceneView,
            DEFAULT_BUFFER_SIZE,
            viewLifecycleOwner.lifecycleScope
        )

        binding.sceneView.apply {
            planeRenderer.isVisible = App.mode == App.ADMIN_MODE
            instructions.enabled = false
            onArFrame = { frame ->
                onDrawFrame(frame)
            }
            configureSession { arSession, config ->
                config.depthMode = Config.DepthMode.AUTOMATIC
                config.focusMode = Config.FocusMode.AUTO
                //config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
            }
            onTap = { node, _, _ ->
            }

            onArSessionFailed = { exception ->
                val message = when (exception) {
                    is UnavailableArcoreNotInstalledException,
                    is UnavailableUserDeclinedInstallationException -> getString(R.string.install_arcode)
                    is UnavailableApkTooOldException -> getString(R.string.update_arcode)
                    is UnavailableSdkTooOldException -> getString(R.string.update_app)
                    is UnavailableDeviceNotCompatibleException -> getString(R.string.no_arcore_support)
                    is CameraNotAvailableException -> getString(R.string.camera_not_available)
                    is SecurityException -> getString(R.string.provide_camera_permission)
                    else -> getString(R.string.failed_to_create_session)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }

        selectNode(null)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){
                mainModel.pathState.collectLatest { pathState ->
                    if (currentPathState?.endEntry != pathState.endEntry) {
                        endPlacingJob?.cancel()
                        currentPathState?.endEntry?.let { end ->
                            treeNodesToModels[end]?.let {
                                drawerHelper.removeNode(it)
                            }
                        }
                    }
                    if (currentPathState?.startEntry != pathState.startEntry) {
                        startPlacingJob?.cancel()
                        currentPathState?.startEntry?.let { start ->
                            treeNodesToModels[start]?.let {
                                drawerHelper.removeNode(it)
                            }
                        }
                    }
                    //         }
                    currentPathState = pathState
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){
                mainModel.confirmationObject.collectLatest { confObject ->
                    confObjectJob?.cancel()
                    confObjectJob = viewLifecycleOwner.lifecycleScope.launch {
                        lastConfObject?.node?.let {
                            drawerHelper.removeNode(it)
                        }
                        confObject?.let {
                            it.node = drawerHelper.placeLabel(
                                confObject.label,
                                confObject.pos,
                                binding.sceneView
                            )
                            lastConfObject = it
                        }
                    }
                }
            }
        }
    }


    private fun onDrawFrame(frame: ArFrame) {

        val camera = frame.camera

        if (camera.trackingState != TrackingState.TRACKING) {
            return
        }

        mainModel.onEvent(
            MainEvent.NewFrame(frame)
        )

        val userPos = Float3(
            frame.camera.displayOrientedPose.tx(),
            frame.camera.displayOrientedPose.ty(),
            frame.camera.displayOrientedPose.tz()
        )

        if (System.currentTimeMillis() - lastPositionTime > POSITION_DETECT_DELAY){
            lastPositionTime = System.currentTimeMillis()
            changeViewablePath(userPos)
        }
    }

    private fun selectNode(node: ArNode?) {
        val treeNode = checkTreeNode(node) ?: checkTreeNode(node?.parentNode as ArNode?)

        selectionJob?.cancel()
        selectionNode?.let { drawerHelper.removeNode(it) }
        treeNode?.let {
            selectionJob = viewLifecycleOwner.lifecycleScope.launch {
                selectionNode = drawerHelper.drawSelection(it, binding.sceneView)
            }
        }
        mainModel.onEvent(MainEvent.NewSelectedNode(treeNode))
    }

    private fun checkSelectedNode(treeNode: TreeNode){
        if (treeAdapter.getArNode(treeNode) == null) {
            selectNode(null)
        }
    }

    private fun changeViewablePath(userPosition: Float3){
        wayBuildingJob?.cancel()
        wayBuildingJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val nodes = currentPathState?.path?.getNearNodes(
                number = VIEWABLE_PATH_NODES,
                position = userPosition
            )
        }
    }

    private fun checkTreeNode(node: ArNode?): TreeNode? {
        treeNodesToModels.inverse[node]?.let { return it }
        treeAdapter.getTreeNode(node)?.let { return it }
        return null
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.sceneView, message, Snackbar.LENGTH_SHORT)
            .show()
    }

    companion object {
        //how many path nodes will be displayed at the moment
        const val VIEWABLE_PATH_NODES = 31
        //how often the check for path and tree redraw will be
        const val POSITION_DETECT_DELAY = 100L
        //image crop for recognition
        val DESIRED_CROP = Pair(0, 0)
    }
}