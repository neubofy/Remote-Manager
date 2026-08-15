package de.felixnuesse.extract.onboarding

interface SlideLeaveInterface {

    fun allowSlideLeave(id: String): Boolean

    fun onSlideLeavePrevented(id: String)
}