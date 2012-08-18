/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gephi.fdeb.utils;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.gephi.fdeb.FDEBCompatibilityRecord;
import org.gephi.fdeb.FDEBLayoutData;
import org.gephi.graph.api.*;
import org.gephi.utils.StatisticsUtils;

/**
 *
 * @author megaterik
 */
public class FDEBUtilities {

    static final double EPS = 1e-6;
    /*
     * Dynamical calculing of K, in jflowmap K =
     * AxisMarks.ordAlpha(Math.min(xStats.getMax() - xStats.getMin(),
     * yStats.getMax() - yStats.getMin()) / 1000);
     */

    /**
     * Not used for now, it does not work well on big graphs.
     */
    static public double calculateSpringConstant(Graph graph) {
        double minX = Integer.MAX_VALUE;
        double maxX = Integer.MIN_VALUE;
        double minY = Integer.MAX_VALUE;
        double maxY = Integer.MIN_VALUE;
        for (Node node : graph.getNodes()) {
            minX = Math.min(minX, node.getNodeData().x());
            minY = Math.min(minY, node.getNodeData().y());

            maxX = Math.max(maxX, node.getNodeData().x());
            maxY = Math.max(maxY, node.getNodeData().y());
        }
        return (maxX - minX + maxY - minY) / 1000.0;
    }

    /*
     * Gives fair estimations for good results in small and medium graphs. Big graphs might need greater values.
     */
    static public double calculateInitialStepSize(Graph graph) {
        double higherDistances2;
        double xDiff, yDiff;
        
        Edge edges[] = graph.getEdges().toArray();
        Number distances2[] = new Number[edges.length];
        for (int i = 0; i < edges.length; i++) {
            Edge edge = edges[i];
            xDiff = edge.getSource().getNodeData().x() - edge.getTarget().getNodeData().x();
            yDiff = edge.getSource().getNodeData().y() - edge.getTarget().getNodeData().y();
            distances2[i] = (xDiff * xDiff + yDiff * yDiff);
        }
        higherDistances2 = StatisticsUtils.quartile3(distances2).doubleValue();

        return Math.sqrt(higherDistances2) / 50;
    }

    static public void divideEdge(Edge edge, double subdivisionPointsPerEdge) {
        if (edge.isSelfLoop()) {
            return;
        }
        FDEBLayoutData data = (FDEBLayoutData) edge.getEdgeData().getLayoutData();
        Point.Double[] subdivisionPoints = new Point.Double[(int) (subdivisionPointsPerEdge + 2)];//+source/target
        double totalLength = 0.0;
        for (int i = 1; i < data.subdivisionPoints.length; i++) {
            totalLength += Point.Double.distance(data.subdivisionPoints[i - 1].x, data.subdivisionPoints[i - 1].y,
                    data.subdivisionPoints[i].x, data.subdivisionPoints[i].y);
        }
        double lengthPerPoint = totalLength / (((int) subdivisionPointsPerEdge) + 1);
        double remainingLengthPerPoint = lengthPerPoint;
        int curSubdivisionPoint = 1;
        for (int i = 1; i < data.subdivisionPoints.length; i++) {
            double lengthOfEdge = Point.Double.distance(data.subdivisionPoints[i - 1].x, data.subdivisionPoints[i - 1].y,
                    data.subdivisionPoints[i].x, data.subdivisionPoints[i].y);
            double remainingLengthOfEdge = lengthOfEdge;
            while (remainingLengthPerPoint < remainingLengthOfEdge) {
                remainingLengthOfEdge -= remainingLengthPerPoint;
                double coef = (lengthOfEdge - remainingLengthOfEdge) / (lengthOfEdge);//0.0--source, 1.0 -- target
                subdivisionPoints[curSubdivisionPoint] = new Point2D.Double(
                        data.subdivisionPoints[i - 1].x
                        + coef * (data.subdivisionPoints[i].x - data.subdivisionPoints[i - 1].x),
                        data.subdivisionPoints[i - 1].y
                        + coef * (data.subdivisionPoints[i].y - data.subdivisionPoints[i - 1].y));
                curSubdivisionPoint++;
                remainingLengthPerPoint = lengthPerPoint;
            }
            remainingLengthPerPoint -= remainingLengthOfEdge;
        }
        subdivisionPoints[0] = data.subdivisionPoints[0];//source
        subdivisionPoints[subdivisionPoints.length - 1] = data.subdivisionPoints[data.subdivisionPoints.length - 1];//target

        data.subdivisionPoints = subdivisionPoints;
    }

    static public void createCompatibilityRecords(double compatibilityThreshold, Graph graph, FDEBCompatibilityComputator computator) {
        Edge[] edges = graph.getEdges().toArray();
        List<FDEBCompatibilityRecord> compatibleEdgeLists[] = new List[edges.length];
        for (int i = 0; i < compatibleEdgeLists.length; i++) {
            compatibleEdgeLists[i] = new ArrayList<FDEBCompatibilityRecord>();
        }

        int numEdges = edges.length;

        for (int i = 0; i < edges.length; i++) {
            ((FDEBLayoutData) edges[i].getEdgeData().getLayoutData()).intensity = 0;
        }

        for (int i = 0; i < numEdges; i++) {
            for (int j = 0; j < i; j++) {

                double compatibility = computator.calculateCompatibility(edges[i], edges[j]);
                ((FDEBLayoutData) edges[i].getEdgeData().getLayoutData()).intensity += compatibility * compatibility;
                ((FDEBLayoutData) edges[j].getEdgeData().getLayoutData()).intensity += compatibility * compatibility;
                if (Math.abs(compatibility) >= compatibilityThreshold) {
                    compatibleEdgeLists[i].add(new FDEBCompatibilityRecord(compatibility, edges[j]));
                    compatibleEdgeLists[j].add(new FDEBCompatibilityRecord(compatibility, edges[i]));
                }
            }
        }

        for (int i = 0; i < edges.length; i++) {
            ((FDEBLayoutData) edges[i].getEdgeData().getLayoutData()).similarEdges = compatibleEdgeLists[i].toArray(new FDEBCompatibilityRecord[0]);
        }
    }
    
    static public void createCompatibilityRecords(Edge edge, double compatibilityThreshold, Graph graph, FDEBCompatibilityComputator computator) {
        ArrayList<FDEBCompatibilityRecord> similar = new ArrayList<FDEBCompatibilityRecord>();
        if (edge.isSelfLoop()) {
            ((FDEBLayoutData) edge.getEdgeData().getLayoutData()).similarEdges = new FDEBCompatibilityRecord[0];
        }
        ((FDEBLayoutData) edge.getEdgeData().getLayoutData()).intensity = 0;
        for (Edge probablySimilarEdge : graph.getEdges().toArray()) {
            if (probablySimilarEdge.isSelfLoop() || probablySimilarEdge == edge) {
                continue;
            }
            double compatibility = computator.calculateCompatibility(edge, probablySimilarEdge);
            ((FDEBLayoutData) edge.getEdgeData().getLayoutData()).intensity += compatibility * compatibility;
            if (compatibility < compatibilityThreshold) {
                continue;
            }
            similar.add(new FDEBCompatibilityRecord(compatibility, probablySimilarEdge));
        }
        ((FDEBLayoutData) edge.getEdgeData().getLayoutData()).similarEdges = new FDEBCompatibilityRecord[similar.size()];
        for (int i = 0; i < similar.size(); i++) {
            ((FDEBLayoutData) edge.getEdgeData().getLayoutData()).similarEdges[i] = similar.get(i);
        }
    }

    static public void updateNewSubdivisionPoints(Edge edge, double springConstant, double stepSize, boolean useInverseQuadraticModel) {
        if (edge.isSelfLoop()) {
            return;
        }
        FDEBLayoutData data = edge.getEdgeData().getLayoutData();
        double k = springConstant / (data.length * (data.subdivisionPoints.length - 1));
        for (int i = 1; i < data.subdivisionPoints.length - 1; i++) {//first and last are fixed
            double Fsi_x = (data.subdivisionPoints[i - 1].x - data.subdivisionPoints[i].x)
                    + (data.subdivisionPoints[i + 1].x - data.subdivisionPoints[i].x);
            double Fsi_y = (data.subdivisionPoints[i - 1].y - data.subdivisionPoints[i].y)
                    + (data.subdivisionPoints[i + 1].y - data.subdivisionPoints[i].y);

            Fsi_x *= k;
            Fsi_y *= k;

            double Fei_x = 0;
            double Fei_y = 0;


            for (FDEBCompatibilityRecord record : (((FDEBLayoutData) edge.getEdgeData().getLayoutData())).similarEdges) {
                Edge moveEdge = record.edgeWith;
                if (moveEdge.isSelfLoop()) {
                    continue;
                }

                FDEBLayoutData moveData = moveEdge.getEdgeData().getLayoutData();
                double v_x = moveData.subdivisionPoints[i].x - data.subdivisionPoints[i].x;
                double v_y = moveData.subdivisionPoints[i].y - data.subdivisionPoints[i].y;

                if (Math.abs(v_x) > EPS || Math.abs(v_y) > EPS) {
                    double len_sq = v_x * v_x + v_y * v_y;
                    double len = Math.sqrt(len_sq);

                    double m;
                    if (useInverseQuadraticModel) {
                        m = (record.compatibility / len / len_sq);
                    } else {
                        m = (record.compatibility / len_sq);
                    }

                    if (Math.abs(m * stepSize) > 1.0) {  // this condition is to reduce the "hairy" effect:
                        // a point shouldn't be moved farther than to the
                        // point which attracts it
                        m = Math.signum(m) / stepSize;
                        // TODO: this force difference shouldn't be neglected
                        // instead it should make it more difficult to move the
                        // point from it's current position: this should reduce
                        // the effect even more
                    }

                    v_x *= m;
                    v_y *= m;

                    Fei_x += v_x;
                    Fei_y += v_y;
                }
            }
            /*
             * store new coordinates to update them simultaniously
             */
            data.newSubdivisionPoints[i] = new Point.Double(data.subdivisionPoints[i].x + stepSize * (Fei_x + Fsi_x),
                    data.subdivisionPoints[i].y + stepSize * (Fei_y + Fsi_y));

        }
    }

    static public void updateNewSubdivisionPointsInLowMemoryMode(Edge edge, double springConstant, double stepSize, boolean useInverseQuadraticModel,
            Graph graph, FDEBCompatibilityComputator comp, double threshold) {
        if (edge.isSelfLoop()) {
            return;
        }
        FDEBLayoutData data = edge.getEdgeData().getLayoutData();
        double k = springConstant / (data.length * (data.subdivisionPoints.length - 1));
        for (int i = 1; i < data.subdivisionPoints.length - 1; i++)//first and last are fixed
        {
            double Fsi_x = (data.subdivisionPoints[i - 1].x - data.subdivisionPoints[i].x)
                    + (data.subdivisionPoints[i + 1].x - data.subdivisionPoints[i].x);
            double Fsi_y = (data.subdivisionPoints[i - 1].y - data.subdivisionPoints[i].y)
                    + (data.subdivisionPoints[i + 1].y - data.subdivisionPoints[i].y);

            {
                Fsi_x *= k;
                Fsi_y *= k;
            }
            double Fei_x = 0;
            double Fei_y = 0;



            for (Edge moveEdge : graph.getEdges().toArray()) {
                if (moveEdge.isSelfLoop() || edge == moveEdge) {
                    continue;
                }
                double compatibility = comp.calculateCompatibility(edge, moveEdge);
                if (compatibility < threshold) {
                    continue;
                }

                FDEBLayoutData moveData = moveEdge.getEdgeData().getLayoutData();
                double v_x = moveData.subdivisionPoints[i].x - data.subdivisionPoints[i].x;
                double v_y = moveData.subdivisionPoints[i].y - data.subdivisionPoints[i].y;

                if (Math.abs(v_x) > EPS || Math.abs(v_y) > EPS) {
                    double len_sq = v_x * v_x + v_y * v_y;
                    double len = Math.sqrt(len_sq);

                    double m;
                    if (useInverseQuadraticModel) {
                        m = (compatibility / len / len_sq);
                    } else {
                        m = (compatibility / len_sq);
                    }

                    if (Math.abs(m * stepSize) > 1.0) {  // this condition is to reduce the "hairy" effect:
                        // a point shouldn't be moved farther than to the
                        // point which attracts it
                        m = Math.signum(m) / stepSize;
                        // TODO: this force difference shouldn't be neglected
                        // instead it should make it more difficult to move the
                        // point from it's current position: this should reduce
                        // the effect even more
                    }

                    v_x *= m;
                    v_y *= m;

                    Fei_x += v_x;
                    Fei_y += v_y;
                }
            }
            /*
             * store new coordinates to update them simultaniously
             */
            data.newSubdivisionPoints[i] = new Point.Double(data.subdivisionPoints[i].x + stepSize * (Fei_x + Fsi_x),
                    data.subdivisionPoints[i].y + stepSize * (Fei_y + Fsi_y));

        }
    }
    public static int passed = 0;
    public static double passedValue = 0;
    public static int total = 0;
    public static int visited = 0;
}
