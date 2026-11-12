package com.resequencetwin.control.hermod

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * `hermod.subscribe.*`-gated Paho wiring that turns the twin into a live Sparkplug host for hermod's
 * governance Edge Node. Mirrors [com.resequencetwin.control.drift.DriftConfig]'s `@Value`-gated bean
 * pattern; default OFF so non-subscribe deployments and unit tests are unaffected.
 *
 * The [HermodGovernanceSubscriber] host logic is unit-tested directly; this Paho glue is gate-exercised.
 */
@Configuration
class HermodSubscribeConfig(
    @Value("\${hermod.subscribe.enabled:false}") private val enabled: Boolean,
    @Value("\${hermod.subscribe.mqtt-url:tcp://localhost:1883}") private val mqttUrl: String,
    @Value("\${hermod.subscribe.group:Hermod}") private val group: String,
    @Value("\${hermod.subscribe.edge:Governance}") private val edge: String,
) {
    /** Always available so [DriftMonitor] can merge annotations even when the subscriber is off. */
    @Bean
    fun reconciliationStore(): ReconciliationStore = ReconciliationStore()

    /**
     * Live Paho subscriber, wired only when `hermod.subscribe.enabled=true`. Subscribes the whole node
     * namespace `spBv1.0/{group}/+/{edge}` (NBIRTH + NDATA + …) and forwards each message to the host.
     */
    @Bean(destroyMethod = "close")
    fun hermodMqttClient(store: ReconciliationStore): AutoCloseable? {
        if (!enabled) return null
        val subscriber = HermodGovernanceSubscriber(edge) { key, ann -> store.put(key, ann) }
        val client = MqttClient(mqttUrl, "resequence-twin-$group-$edge", MemoryPersistence())
        return try {
            client.connect(MqttConnectOptions().apply {
                isCleanSession = true
                // first-cut: auto-reconnect only kicks in AFTER an initial successful connect; if the
                // broker is down at startup the subscriber stays inert until the app restarts.
                isAutomaticReconnect = true
            })
            val topicFilter = "spBv1.0/$group/+/$edge"
            client.subscribe(topicFilter, 1) { topic, message -> subscriber.onMessage(topic, message.payload) }
            log.info("hermod governance subscriber connected to {} on {}", mqttUrl, topicFilter)
            // Clean shutdown: disconnect BEFORE close (Paho's close() throws if still connected).
            AutoCloseable {
                try { if (client.isConnected) client.disconnect() } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            log.warn("hermod governance subscriber failed to connect to {} (continuing without): {}", mqttUrl, e.toString())
            try { client.close() } catch (_: Exception) {}
            null
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(HermodSubscribeConfig::class.java)
    }
}
