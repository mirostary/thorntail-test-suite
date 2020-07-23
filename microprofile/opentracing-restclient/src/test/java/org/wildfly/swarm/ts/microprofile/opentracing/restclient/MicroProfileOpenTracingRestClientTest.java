package org.wildfly.swarm.ts.microprofile.opentracing.restclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.fluent.Request;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.arquillian.DefaultDeployment;
import org.wildfly.swarm.ts.common.docker.Docker;
import org.wildfly.swarm.ts.common.docker.DockerContainers;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@RunWith(Arquillian.class)
@DefaultDeployment
public class MicroProfileOpenTracingRestClientTest {
    @ClassRule
    public static Docker jaegerContainer = DockerContainers.jaeger();

    @Test
    @InSequence(1)
    @RunAsClient
    public void applicationRequest_injectedRestClient() throws IOException {
        String response = Request.Get("http://localhost:8080/rest/client/injected").execute().returnContent().asString();
        assertThat(response).isEqualTo("Hello from traced service");
    }

    @Test
    @InSequence(2)
    @RunAsClient
    public void applicationRequest_programmaticRestClient() throws IOException {
        String response = Request.Get("http://localhost:8080/rest/client/programmatic").execute().returnContent().asString();
        assertThat(response).isEqualTo("Hello from traced service");
    }

    @Test
    @InSequence(3)
    @RunAsClient
    public void applicationRequest_jaxrsClient() throws IOException {
        String response = Request.Get("http://localhost:8080/rest/client/jaxrs").execute().returnContent().asString();
        assertThat(response).isEqualTo("Hello from traced service");
    }

    @Test
    @InSequence(4)
    @RunAsClient
    public void traces() {
        String[] methods = {"injected", "programmatic", "jaxrs"};

        // What about some counter to see if time is enough and where it drops !!!!
        AtomicInteger counter = new AtomicInteger();

        // the tracer inside the application doesn't send traces to the Jaeger server immediately,
        // they are batched, so we need to wait a bit
        await().atMost(100, TimeUnit.SECONDS).untilAsserted(() -> {
            counter.incrementAndGet();
            System.out.println("------------------------------------------");
            System.out.println(counter);
            String response = Request.Get("http://localhost:16686/api/traces?service=test-traced-service").execute().returnContent().asString();
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            assertThat(json.has("data")).isTrue();
            JsonArray data = json.getAsJsonArray("data");
            assertThat(data.size()).isEqualTo(methods.length);
            for (int i = 0; i < methods.length; i++) {
                String method = methods[i];
                JsonObject trace = data.get(i).getAsJsonObject();
                assertThat(trace.has("spans")).isTrue();
                JsonArray spans = trace.getAsJsonArray("spans");
                assertThat(spans).hasSize(4);
                assertThat(spans).anySatisfy(element -> {
                    JsonObject span = element.getAsJsonObject();
                    assertThat(span.get("operationName").getAsString())
                            .isEqualTo("org.wildfly.swarm.ts.microprofile.opentracing.restclient.MyService.hello");
                    assertThat(span.has("logs")).isTrue();
                    JsonArray logs = span.getAsJsonArray("logs");
                    assertThat(logs).hasSize(1);
                    JsonObject log = logs.get(0).getAsJsonObject();
                    assertThat(log.getAsJsonArray("fields").get(0).getAsJsonObject().get("value").getAsString())
                            .isEqualTo("Hello tracer");
                });
                assertThat(spans).anySatisfy(element -> {
                    JsonObject span = element.getAsJsonObject();
                    assertThat(span.get("operationName").getAsString())
                            .isEqualTo("GET:org.wildfly.swarm.ts.microprofile.opentracing.restclient.HelloResource.get");
                    assertThat(span.has("tags")).isTrue();
                    JsonArray tags = span.getAsJsonArray("tags");
                    for (JsonElement tagElement : tags) {
                        JsonObject tag = tagElement.getAsJsonObject();
                        switch (tag.get("key").getAsString()) {
                            case "http.method":
                                assertThat(tag.get("value").getAsString()).isEqualTo("GET");
                                break;
                            case "http.url":
                                assertThat(tag.get("value").getAsString()).isEqualTo("http://localhost:8080/rest/hello");
                                break;
                            case "http.status.code":
                                assertThat(tag.get("value").getAsInt()).isEqualTo(200);
                                break;
                            case "component":
                                assertThat(tag.get("value").getAsString()).isEqualTo("jaxrs");
                                break;
                            case "span.kind":
                                assertThat(tag.get("value").getAsString()).isEqualTo("server");
                                break;
                        }
                    }
                });
                assertThat(spans).anySatisfy(element -> {
                    JsonObject span = element.getAsJsonObject();
                    assertThat(span.get("operationName").getAsString()).isEqualTo("GET");
                    assertThat(span.has("tags")).isTrue();
                    JsonArray tags = span.getAsJsonArray("tags");
                    for (JsonElement tagElement : tags) {
                        JsonObject tag = tagElement.getAsJsonObject();
                        switch (tag.get("key").getAsString()) {
                            case "http.method":
                                assertThat(tag.get("value").getAsString()).isEqualTo("GET");
                                break;
                            case "http.url":
                                assertThat(tag.get("value").getAsString()).isEqualTo("http://localhost:8080/rest/hello");
                                break;
                            case "http.status.code":
                                assertThat(tag.get("value").getAsInt()).isEqualTo(200);
                                break;
                            case "component":
                                assertThat(tag.get("value").getAsString()).isEqualTo("jaxrs");
                                break;
                            case "span.kind":
                                assertThat(tag.get("value").getAsString()).isEqualTo("client");
                                break;
                        }
                    }
                });
                assertThat(spans).anySatisfy(element -> {
                    JsonObject span = element.getAsJsonObject();
                    assertThat(span.get("operationName").getAsString())
                            .isEqualTo("GET:org.wildfly.swarm.ts.microprofile.opentracing.restclient.ClientResource." + method);
                    assertThat(span.has("tags")).isTrue();
                    JsonArray tags = span.getAsJsonArray("tags");
                    for (JsonElement tagElement : tags) {
                        JsonObject tag = tagElement.getAsJsonObject();
                        switch (tag.get("key").getAsString()) {
                            case "http.method":
                                assertThat(tag.get("value").getAsString()).isEqualTo("GET");
                                break;
                            case "http.url":
                                assertThat(tag.get("value").getAsString()).isEqualTo("http://localhost:8080/rest/client/" + method);
                                break;
                            case "http.status.code":
                                assertThat(tag.get("value").getAsInt()).isEqualTo(200);
                                break;
                            case "component":
                                assertThat(tag.get("value").getAsString()).isEqualTo("jaxrs");
                                break;
                            case "span.kind":
                                assertThat(tag.get("value").getAsString()).isEqualTo("server");
                                break;
                        }
                    }
                });
            }
        });
    }
}
