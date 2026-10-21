package pl.twojanazwa.aapsremote.ui.platform

import dagger.Provides
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.ui.commands.CommandRunScope
import javax.inject.Singleton

class CommandsModuleTest {

    @Test
    fun `CMDUI-27 modul ma jedna metode dostarczajaca zakres z Provides Singleton i CommandRunScope`() {
        val methods = CommandsModule::class.java.declaredMethods
            .filter { it.returnType == CoroutineScope::class.java }
        assertEquals(1, methods.size)
        val method = methods.single()
        assertNotNull(method.getAnnotation(Provides::class.java))
        assertNotNull(method.getAnnotation(Singleton::class.java))
        assertNotNull(method.getAnnotation(CommandRunScope::class.java))
    }

    @Test
    fun `CMDUI-13 dziecko zakonczone wyjatkiem nie anuluje zakresu`() = runBlocking {
        val scope = CommandsModule.commandRunScope()
        val job = scope.coroutineContext[Job]!!
        try {
            scope.launch { throw IllegalStateException("test") }.join()
            assertTrue(job.isActive)
            val done = CompletableDeferred<Unit>()
            scope.launch { done.complete(Unit) }
            withTimeout(5_000) { done.await() }
        } finally {
            job.cancel()
        }
    }
}
