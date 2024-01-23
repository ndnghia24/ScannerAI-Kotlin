package com.example.scannerai.presentation.preview.state

import com.example.scannerai.domain.utils.Path
import com.example.scannerai.domain.utils.tree.TreeNode

data class PathState(
    val startEntry: TreeNode.Entry? = null,
    val endEntry: TreeNode.Entry? = null,
    val path: Path? = null
)
