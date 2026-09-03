package com.aeibi.design.data.sessions

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun cancelledCallerStillPersistsTurnFinish() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        val job = launch {
            try {
                awaitCancellation()
            } finally {
                repository.finishTurn(
                    sessionId = "session",
                    turnId = "turn",
                    status = TurnStatus.CANCELLED,
                    partialResponse = "Partial answer",
                    partialReasoning = "Partial reasoning"
                )
            }
        }

        testScheduler.runCurrent()
        job.cancelAndJoin()

        val entry = repository.observeEntries("session").first().single()
        assertEquals(
            TurnFinishedPayload(
                TurnStatus.CANCELLED,
                failure = null,
                partialResponse = "Partial answer",
                partialReasoning = "Partial reasoning"
            ),
            repository.decodeTurnFinished(entry)
        )
    }

    @Test
    fun cancelledTurnKeepsCompletedItemsInModelContext() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage(
            "session",
            "turn",
            MessageOrigin.USER,
            Message.User("Update the app", RequestMetaInfo.Empty)
        )
        repository.appendMessage(
            "session",
            "turn",
            MessageOrigin.ASSISTANT,
            Message.Assistant("I updated the first file.", ResponseMetaInfo.Empty)
        )
        repository.finishTurn("session", "turn", TurnStatus.CANCELLED)

        assertEquals(
            listOf(
                "Update the app",
                "I updated the first file.",
                "The previous turn was interrupted on purpose. Any interrupted tool calls may have partially executed. Inspect the workspace before continuing."
            ),
            repository.loadModelMessages("session").map { it.textContent() }
        )
    }

    @Test
    fun contextReplacementKeepsOnlyReplacementAndLaterMessagesInModelContext() = runTest {
        val repository = SessionRepository(InMemorySessionDao())

        repository.appendMessage("session", "turn-1", MessageOrigin.USER, Message.User("First", RequestMetaInfo.Empty))
        repository.appendMessage(
            "session",
            "turn-1",
            MessageOrigin.ASSISTANT,
            Message.Assistant("First response", ResponseMetaInfo.Empty)
        )
        repository.replaceContext(
            "session",
            "turn-2",
            listOf(Message.User("Summary", RequestMetaInfo.Empty))
        )
        repository.appendMessage("session", "turn-2", MessageOrigin.USER, Message.User("Second", RequestMetaInfo.Empty))

        assertEquals(
            listOf("Summary", "Second"),
            repository.loadModelMessages("session").map { it.textContent() }
        )
    }

    @Test
    fun interruptedToolCallGetsUnknownOutcomeResult() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage(
            "session",
            "turn",
            MessageOrigin.ASSISTANT,
            Message.Assistant(
                MessagePart.Tool.Call(id = "call-1", tool = "write_file", args = "{}"),
                ResponseMetaInfo.Empty
            )
        )

        repository.repairInterruptedToolCalls("session")

        val result = repository.loadModelMessages("session").last() as Message.User
        val part = result.parts.single() as MessagePart.Tool.Result
        assertEquals("call-1", part.id)
        assertTrue(part.isError)
        assertEquals(
            "The tool execution was interrupted and its outcome is unknown. Inspect the workspace before retrying it.",
            part.output
        )
    }

    @Test
    fun failedTurnKeepsStructuredFailureOutsideModelContext() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage("session", "turn", MessageOrigin.USER, Message.User("Hello", RequestMetaInfo.Empty))
        repository.finishTurn(
            sessionId = "session",
            turnId = "turn",
            status = TurnStatus.FAILED,
            failure = AgentFailure("The selected provider has no API key", "CONFIGURATION")
        )

        val entries = repository.observeEntries("session").first()
        val finished = entries.last()

        assertEquals(
            AgentFailure("The selected provider has no API key", "CONFIGURATION"),
            repository.decodeTurnFinished(finished).failure
        )
        assertEquals(listOf("Hello"), repository.loadModelMessages("session").map { it.textContent() })
    }

    @Test
    fun cancelledTurnReplaysPartialResponseAsAssistantMessage() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage(
            "session",
            "turn",
            MessageOrigin.USER,
            Message.User("Write a long story", RequestMetaInfo.Empty)
        )
        repository.finishTurn(
            sessionId = "session",
            turnId = "turn",
            status = TurnStatus.CANCELLED,
            partialResponse = "Once upon a time, there was a brave",
            partialReasoning = null
        )

        val messages = repository.loadModelMessages("session")

        assertEquals(
            listOf(
                "Write a long story",
                "Once upon a time, there was a brave",
                "The previous turn was interrupted on purpose. Any interrupted tool calls may have partially executed. Inspect the workspace before continuing."
            ),
            messages.map { it.textContent() }
        )
        // partial 以 assistant 角色重放（模型视为自己的历史输出）
        assertTrue("partial 应以 assistant 角色重放", messages[1] is Message.Assistant)
    }

    @Test
    fun cancelledTurnWithoutPartialSkipsAssistantReplay() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage(
            "session",
            "turn",
            MessageOrigin.USER,
            Message.User("Ask me something", RequestMetaInfo.Empty)
        )
        repository.finishTurn("session", "turn", TurnStatus.CANCELLED)

        val texts = repository.loadModelMessages("session").map { it.textContent() }
        // 无 partial → 只有用户消息 + 中断提示
        assertEquals(2, texts.size)
        assertTrue(texts[1].contains("interrupted"))
    }
}
