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

package io.rsocket.routing.broker.spring;

import io.rsocket.routing.frames.Address;
import io.rsocket.routing.frames.RouteSetup;

import org.springframework.util.MimeType;

public abstract class MimeTypes {

	/**
	 * Address mime type.
	 */
	public static final MimeType ADDRESS_MIME_TYPE = new MimeType("message",
			Address.ADDRESS);
	/**
	 * Route Setup mime type.
	 */
	public static final MimeType ROUTE_SETUP_MIME_TYPE = new MimeType("message",
			RouteSetup.ROUTE_SETUP);

	private MimeTypes() {

	}


}
