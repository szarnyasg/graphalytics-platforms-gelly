/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package science.atlarge.graphalytics.flink.algorithms.sssp;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.graph.Edge;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.GraphAlgorithm;
import org.apache.flink.graph.Vertex;
import org.apache.flink.graph.spargel.GatherFunction;
import org.apache.flink.graph.spargel.MessageIterator;
import org.apache.flink.graph.spargel.ScatterFunction;
import org.apache.flink.graph.utils.VertexToTuple2Map;
import org.apache.flink.types.NullValue;
import science.atlarge.graphalytics.domain.algorithms.AlgorithmParameters;
import science.atlarge.graphalytics.domain.algorithms.SingleSourceShortestPathsParameters;

public class ScatterGatherSSSP implements GraphAlgorithm<Long, NullValue, Double, DataSet<Tuple2<Long, Double>>> {

	private final long srcVertexId;
	private final Integer maxIterations;

	public ScatterGatherSSSP(AlgorithmParameters params) {
		SingleSourceShortestPathsParameters ssspParams = (SingleSourceShortestPathsParameters)params;
		this.srcVertexId = ssspParams.getSourceVertex();
		this.maxIterations = 100;
	}

	@Override
	public DataSet<Tuple2<Long, Double>> run(Graph<Long, NullValue, Double> input) {

		return input.mapVertices(new InitVerticesMapper(srcVertexId))
				.runScatterGatherIteration(
					new MinDistanceMessenger(),
					new VertexDistanceUpdater(),
					maxIterations
				).getVertices().map(new VertexToTuple2Map<Long, Double>());
	}

	@SuppressWarnings("serial")
	public static final class InitVerticesMapper implements MapFunction<Vertex<Long, NullValue>, Double> {

		private long srcVertexId;

		public InitVerticesMapper(long srcId) {
			this.srcVertexId = srcId;
		}

		public Double map(Vertex<Long, NullValue> value) {
			if (value.f0.equals(srcVertexId)) {
				return 0.0;
			} else {
				return Double.POSITIVE_INFINITY;
			}
		}
	}

	/**
	 * Function that updates the value of a vertex by picking the minimum
	 * distance from all incoming messages.
	 *
	 */
	@SuppressWarnings("serial")
	public static final class VertexDistanceUpdater extends GatherFunction<Long, Double, Double> {

		@Override
		public void updateVertex(Vertex<Long, Double> vertex,
				MessageIterator<Double> inMessages) {

			Double minDistance = Double.MAX_VALUE;

			for (double msg : inMessages) {
				if (msg < minDistance) {
					minDistance = msg;
				}
			}

			if (vertex.getValue() > minDistance) {
				setNewVertexValue(minDistance);
			}
		}
	}

	/**
	 * Distributes the minimum distance associated with a given vertex among all
	 * the target vertices summed up with the edge's value.
	 *
	 */
	@SuppressWarnings("serial")
	public static final class MinDistanceMessenger extends ScatterFunction<Long, Double, Double, Double> {

		@Override
		public void sendMessages(Vertex<Long, Double> vertex) {
			if (vertex.getValue() < Double.POSITIVE_INFINITY) {
				for (Edge<Long, Double> edge : getEdges()) {
					sendMessageTo(edge.getTarget(), vertex.getValue() + edge.getValue());
				}
			}
		}
	}

}
