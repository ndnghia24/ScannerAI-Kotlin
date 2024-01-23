package com.example.scannerai.domain.utils

import com.example.scannerai.domain.utils.tree.Tree

interface Pathfinder {

    suspend fun findWay(
        from: String,
        to: String,
        tree: Tree
    ): Path?

}