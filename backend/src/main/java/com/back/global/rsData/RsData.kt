package com.back.global.rsData

import com.fasterxml.jackson.annotation.JsonIgnore
import lombok.AllArgsConstructor
import lombok.Getter

@AllArgsConstructor
@Getter
class RsData<T> @JvmOverloads constructor(
    val resultCode: String,
    val msg: String,
    val data: T?= null //기본 파라미터 문법
)

{

//    constructor(resultCode: String, msg: String) : this(
//        resultCode,
//        msg,
//        null as T
//    )

    @get:JsonIgnore
    val statusCode: Int
        get() {
            return resultCode.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].toInt()
        }
}
