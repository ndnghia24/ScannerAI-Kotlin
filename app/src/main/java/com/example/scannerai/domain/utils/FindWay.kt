package com.example.scannerai.domain.utils

import com.example.scannerai.domain.utils.tree.Tree

class FindWay(
    private val pathfinder: Pathfinder
) {

    suspend operator fun invoke(
        from: String,
        to: String,
        tree: Tree
    ): Path? {
        return pathfinder.findWay(from, to, tree)
    }
}