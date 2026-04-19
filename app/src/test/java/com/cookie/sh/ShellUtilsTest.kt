package com.cookie.sh

import com.cookie.sh.core.shell.quoteForShell
import org.junit.Assert.assertEquals
import org.junit.Test

class ShellUtilsTest {

    @Test
    fun quoteForShell_escapesSingleQuotes() {
        assertEquals("'it'\"'\"'s-safe'", "it's-safe".quoteForShell())
    }
}
