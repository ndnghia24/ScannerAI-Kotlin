package com.example.scannerai.domain.utils

import android.content.Context
import com.example.scannerai.R

class GetDestinationDesc {

    operator fun invoke(number: String, context: Context): String {

        var building = ""
        var floor = 0

        if (number.length == 1){
            floor = 0
            return "$building, $floor"
        }
        else if (number.length == 3 || number.length == 2){
            if (number[number.length - 2].digitToInt() > 4)
            else {
            }
            if (number.length == 2 ){
                floor = 0
                return "$building, $floor"
            }
        }
        else if (number.length == 4){
            when (number[0]) {
            }
        }
        else {
            return ""
        }

        floor = number[number.length - 3].digitToInt()
        return "$building, $floor"
    }

}