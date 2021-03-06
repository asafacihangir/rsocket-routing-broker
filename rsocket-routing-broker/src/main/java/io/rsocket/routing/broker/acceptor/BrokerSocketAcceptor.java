/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.routing.broker.acceptor;

import java.util.function.BiConsumer;
import java.util.function.Function;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.routing.broker.RSocketIndex;
import io.rsocket.routing.broker.RoutingTable;
import io.rsocket.routing.broker.config.BrokerProperties;
import io.rsocket.routing.broker.rsocket.ErrorOnDisconnectRSocket;
import io.rsocket.routing.broker.rsocket.RoutingRSocketFactory;
import io.rsocket.routing.common.WellKnownKey;
import io.rsocket.routing.frames.BrokerInfo;
import io.rsocket.routing.frames.RouteJoin;
import io.rsocket.routing.frames.RouteSetup;
import io.rsocket.routing.frames.RoutingFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SocketAcceptor for routable connections either from clients or other brokers.
 */
public class BrokerSocketAcceptor implements SocketAcceptor {
	protected static final Logger logger = LoggerFactory
			.getLogger(BrokerSocketAcceptor.class);

	protected final BrokerProperties properties;
	protected final RoutingTable routingTable;
	protected final RSocketIndex rSocketIndex;
	protected final Function<ConnectionSetupPayload, RoutingFrame> payloadExtractor;
	protected final BiConsumer<BrokerInfo, RSocket> brokerInfoConsumer;
	protected final RoutingRSocketFactory routingRSocketFactory;

	public BrokerSocketAcceptor(BrokerProperties properties, RoutingTable routingTable,
			RSocketIndex rSocketIndex, RoutingRSocketFactory routingRSocketFactory,
			Function<ConnectionSetupPayload, RoutingFrame> payloadExtractor,
			BiConsumer<BrokerInfo, RSocket> brokerInfoConsumer) {
		this.routingTable = routingTable;
		this.rSocketIndex = rSocketIndex;
		this.routingRSocketFactory = routingRSocketFactory;
		this.payloadExtractor = payloadExtractor;
		this.properties = properties;
		this.brokerInfoConsumer = brokerInfoConsumer;

		logger.info("Starting Broker {}", properties.getBrokerId());
	}

	@Override
	public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {
		Runnable doCleanup = () -> cleanup();

		try {
			RoutingFrame routingFrame = payloadExtractor.apply(setup);

			logger.debug("accept {}", routingFrame);

			RSocket wrapSendingSocket = wrapSendingSocket(sendingSocket, routingFrame);
			if (routingFrame instanceof BrokerInfo) {
				// this is another broker connecting

				brokerInfoConsumer.accept((BrokerInfo) routingFrame, wrapSendingSocket);

				return finalize(sendingSocket, doCleanup);
			} else if (routingFrame instanceof RouteSetup) {
				RouteSetup routeSetup = (RouteSetup) routingFrame;
				// TODO: metrics
				// TODO: error on disconnect?
				return Mono.defer(() -> {
					// TODO: deal with existing connection for routeSetup.routeId

					// create RouteJoin before indexing or RoutingTable
					RouteJoin routeJoin = toRouteJoin(routeSetup);

					// update index before RoutingTable
					// adds sendingSocket to rSocketIndex for later lookup
					rSocketIndex.put(routeJoin.getRouteId(), wrapSendingSocket,
							routeJoin.getTags());

					// update routing table with incoming route.
					routingTable.add(routeJoin);

					return finalize(sendingSocket, doCleanup);
				});
			}

			throw new IllegalStateException("RouteSetup not found in metadata");
		}
		catch (Exception e) {
			logger.error("Error accepting setup", e);
			doCleanup.run();
			return Mono.error(e);

		}
	}

	private Mono<RSocket> finalize(RSocket sendingSocket, Runnable doCleanup) {
		RSocket receivingSocket = routingRSocketFactory.create();
		Flux.first(receivingSocket.onClose(), sendingSocket.onClose())
				.doFinally(s -> doCleanup.run())
				.subscribe();
		return Mono.just(receivingSocket);
	}

	private void cleanup() {

	}

	private RSocket wrapSendingSocket(RSocket sendingSocket, RoutingFrame routingFrame) {
		ErrorOnDisconnectRSocket rSocket = new ErrorOnDisconnectRSocket(sendingSocket);
		 rSocket.onClose().doFinally(s -> logger.info(
		 		"Closing socket for {}", routingFrame));
		return rSocket;
	}

	/**
	 * Creates RouteJoin with current brokerId and enriches tags with routeId and serviceName.
 	 */
	private RouteJoin toRouteJoin(RouteSetup routeSetup) {
		return RouteJoin.builder()
				.brokerId(properties.getBrokerId())
				.routeId(routeSetup.getRouteId())
				.serviceName(routeSetup.getServiceName())
				.with(routeSetup.getTags())
				.with(WellKnownKey.ROUTE_ID, routeSetup.getRouteId().toString())
				.with(WellKnownKey.SERVICE_NAME, routeSetup.getServiceName())
				.build();
	}
}
