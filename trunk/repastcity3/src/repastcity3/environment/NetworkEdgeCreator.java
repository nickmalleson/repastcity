/*
©Copyright 2012 Nick Malleson
This file is part of RepastCity.

RepastCity is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

RepastCity is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with RepastCity.  If not, see <http://www.gnu.org/licenses/>.
 */

package repastcity3.environment;

import java.util.Arrays;

import repast.simphony.space.graph.EdgeCreator;

public class NetworkEdgeCreator<T> implements EdgeCreator<NetworkEdge<T>, T> {

	/**
	 * Creates an Edge with the specified source, target, direction and weight.
	 * 
	 * @param source
	 *            the edge source
	 * @param target
	 *            the edge target
	 * @param isDirected
	 *            whether or not the edge is directed
	 * @param weight
	 *            the weight of the edge
	 * @return the created edge.
	 */
	@Override
	public NetworkEdge<T> createEdge(T source, T target, boolean isDirected, double weight) {
		return new NetworkEdge<T>(source, target, isDirected, weight, Arrays
				.asList(new String[] { "testingEdgeCreator" }));
	}

	/**
	 * Gets the edge type produced by this EdgeCreator.
	 * 
	 * @return the edge type produced by this EdgeCreator.
	 */
	@Override
	@SuppressWarnings("rawtypes")
	public Class<NetworkEdge> getEdgeType() {
		return NetworkEdge.class;
	}

}
