package com.titankeys.autocorrect

import com.titankeys.keyboard.Layout
import kotlin.math.hypot

/** Which letters sit next to each other on the Titan 2 Elite, diagonals included. */
object KeyNeighbours {
    private val near = BooleanArray(26 * 26)

    init {
        val keys = ArrayList<Triple<Int, Int, Int>>()
        for (row in 0..2) for ((column, key) in Layout.rows[row].withIndex())
            if (Layout.isLetter(key)) keys += Triple(key[0] - 'A', row, column)
        for (first in keys) for (second in keys) if (first.first != second.first
            && hypot((first.second - second.second).toDouble(), (first.third - second.third).toDouble()) <= 1.5)
            near[first.first * 26 + second.first] = true
    }

    fun near(a: Char, b: Char): Boolean {
        val first = a.lowercaseChar() - 'a'
        val second = b.lowercaseChar() - 'a'
        return first in 0..25 && second in 0..25 && near[first * 26 + second]
    }
}
