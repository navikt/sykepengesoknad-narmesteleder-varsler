package no.nav.helse.flex.mockdispatcher

import mockwebserver3.MockResponse
import mockwebserver3.QueueDispatcher
import mockwebserver3.RecordedRequest
import no.nav.helse.flex.serialisertTilString
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.concurrent.CopyOnWriteArrayList

abstract class FellesQueueDispatcher<T : Any>(
    private val defaultFactory: (RecordedRequest) -> MockResponse,
) : QueueDispatcher() {
    private val mottatteRequests = CopyOnWriteArrayList<RecordedRequest>()

    init {
        registrer(this)
    }

    fun requests(): List<RecordedRequest> = mottatteRequests.toList()

    fun enqueueResponse(response: MockResponse) {
        enqueue(response)
    }

    fun enqueue(objekt: T) {
        enqueue(withContentTypeApplicationJson { MockResponse(body = objekt.serialisertTilString()) })
    }

    fun harResponserIgjen(): Boolean = responseQueue.isNotEmpty()

    override fun dispatch(request: RecordedRequest): MockResponse {
        mottatteRequests.add(request)
        return responseQueue.poll() ?: defaultFactory(request)
    }

    override fun clear() {
        super.clear()
        mottatteRequests.clear()
    }

    companion object {
        private val registrerte = mutableSetOf<FellesQueueDispatcher<*>>()

        private fun registrer(dispatcher: FellesQueueDispatcher<*>) {
            registrerte.add(dispatcher)
        }

        fun alle(): List<FellesQueueDispatcher<*>> = registrerte.toList()
    }
}

fun withContentTypeApplicationJson(createMockResponse: () -> MockResponse): MockResponse =
    createMockResponse()
        .newBuilder()
        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()
