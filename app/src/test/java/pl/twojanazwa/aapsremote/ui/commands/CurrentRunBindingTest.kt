package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

class CurrentRunBindingTest {
    @Test
    fun `CMDUI-27 CurrentRun jest Singleton z jedynym konstruktorem Inject bez parametrow`() {
        assertNotNull(CurrentRun::class.java.getAnnotation(Singleton::class.java))
        val constructors = CurrentRun::class.java.declaredConstructors
        assertEquals(1, constructors.size)
        val constructor = constructors.single()
        assertEquals(0, constructor.parameterCount)
        assertNotNull(constructor.getAnnotation(Inject::class.java))
    }
}
