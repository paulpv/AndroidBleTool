package com.github.paulpv.androidbletool.exceptions

open class BleException : RuntimeException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(throwable: Throwable?) : super(throwable)
    constructor(message: String?, throwable: Throwable?) : super(message, throwable)
}