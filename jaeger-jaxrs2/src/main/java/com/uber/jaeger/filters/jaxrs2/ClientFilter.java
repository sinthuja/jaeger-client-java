/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.uber.jaeger.filters.jaxrs2;

import com.uber.jaeger.context.TraceContext;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import java.io.IOException;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@ConstrainedTo(RuntimeType.CLIENT)
@Slf4j
public class ClientFilter implements ClientRequestFilter, ClientResponseFilter {
  private final Tracer tracer;
  private final TraceContext traceContext;

  public ClientFilter(Tracer tracer, TraceContext traceContext) {
    this.tracer = tracer;
    this.traceContext = traceContext;
  }

  @Override
  public void filter(ClientRequestContext clientRequestContext) throws IOException {
    try {
      Tracer.SpanBuilder clientSpanBuilder = tracer.buildSpan(clientRequestContext.getMethod());
      if (!traceContext.isEmpty()) {
        clientSpanBuilder.asChildOf(traceContext.getCurrentSpan());
      }
      Span clientSpan = clientSpanBuilder.startManual();

      Tags.SPAN_KIND.set(clientSpan, Tags.SPAN_KIND_CLIENT);
      Tags.HTTP_URL.set(clientSpan, clientRequestContext.getUri().toString());
      Tags.PEER_HOSTNAME.set(clientSpan, clientRequestContext.getUri().getHost());

      tracer.inject(
          clientSpan.context(),
          Format.Builtin.HTTP_HEADERS,
          new ClientRequestCarrier(clientRequestContext));

      clientRequestContext.setProperty(Constants.CURRENT_SPAN_CONTEXT_KEY, clientSpan);
    } catch (Exception e) {
      log.error("Client Filter Request:", e);
    }
  }

  @Override
  public void filter(
      ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext)
      throws IOException {
    try {
      Span clientSpan =
          (io.opentracing.Span)
              clientRequestContext.getProperty(Constants.CURRENT_SPAN_CONTEXT_KEY);
      if (clientSpan != null) {
        Tags.HTTP_STATUS.set(clientSpan, clientResponseContext.getStatus());
        clientSpan.finish();
      }
    } catch (Exception e) {
      log.error("Client Filter Response:", e);
    }
  }
}
