package org.gephi.bundler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.gephi.edgelayout.spi.EdgeLayout;
import org.gephi.edgelayout.spi.EdgeLayoutBuilder;
import org.gephi.fdeb.FDEBCompatibilityRecord;
import org.gephi.fdeb.FDEBLayoutData;
import org.gephi.fdeb.demo.multithreading.FDEBCompatibilityRecordsTask;
import org.gephi.fdeb.demo.multithreading.FDEBForceCalculationTask;
import org.gephi.fdeb.utils.FDEBUtilities;
import org.gephi.graph.api.Edge;
import org.openide.util.Exceptions;

/**
 *
 * @author megaterik
 */
public class FDEBBundlerMultithreading extends FDEBAbstractBundler implements EdgeLayout {

    public FDEBBundlerMultithreading(EdgeLayoutBuilder layoutBuilder) {
        super(layoutBuilder);
    }
    int numberOfTasks = 8;
    ExecutorService executor;

    @Override
    public void initAlgo() {
        executor = Executors.newCachedThreadPool();

        for (Edge edge : graphModel.getGraph().getEdges().toArray()) {
            edge.getEdgeData().setLayoutData(
                    new FDEBLayoutData(edge.getSource().getNodeData().x(), edge.getSource().getNodeData().y(),
                    edge.getTarget().getNodeData().x(), edge.getTarget().getNodeData().y()));
        }
        cycle = 1;
        setConverged(false);
        stepSize = stepSizeAtTheBeginning;
        iterationsPerCycle = iterationsPerCycleAtTheBeginning;
        subdivisionPointsPerEdge = 1;//start and end doesnt count
        System.out.println("K " + springConstant);

        createCompatibilityLists();
    }

    /*
     * Use similar method to ForceAtlas-2
     */
    @Override
    public void goAlgo() {

        for (Edge edge : graphModel.getGraph().getEdges().toArray()) {
            ((FDEBLayoutData) edge.getEdgeData().getLayoutData()).newSubdivisionPoints = Arrays.copyOf(((FDEBLayoutData) edge.getEdgeData().getLayoutData()).subdivisionPoints,
                    ((FDEBLayoutData) edge.getEdgeData().getLayoutData()).subdivisionPoints.length);
        }

        System.err.println("Next iteration");
        for (int step = 0; step < iterationsPerCycle; step++) {

            Future[] calculationTasks = new Future[numberOfTasks];
            int cedges = graphModel.getGraph().getEdgeCount();
            Edge[] edges = graphModel.getGraph().getEdges().toArray();
            for (int i = 0; i < numberOfTasks; i++) {
                if (!useLowMemoryMode) {
                    calculationTasks[i] = executor.submit(new FDEBForceCalculationTask(edges, cedges * i / numberOfTasks,
                            Math.min(cedges, cedges * (i + 1) / numberOfTasks), springConstant, stepSize, useInverseQuadraticModel), computator);
                } else {
                    calculationTasks[i] = executor.submit(new FDEBForceCalculationTask(edges, cedges * i / numberOfTasks,
                            Math.min(cedges, cedges * (i + 1) / numberOfTasks), springConstant, stepSize, useInverseQuadraticModel, computator, compatibilityThreshold, graphModel.getGraph()));
                }
            }

            for (int i = 0; i < calculationTasks.length; i++) {
                try {
                    if (calculationTasks[i] == null) {
                        System.err.println("o_O");
                    }
                    calculationTasks[i].get();
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (ExecutionException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

            for (Edge edge : edges) {
                FDEBLayoutData data = edge.getEdgeData().getLayoutData();
                System.arraycopy(data.newSubdivisionPoints, 0, data.subdivisionPoints, 0, data.newSubdivisionPoints.length);
            }
        }


        if (cycle == numCycles) {
            setConverged(true);
        } else {
            prepareForTheNextStep();
        }
    }

    void prepareForTheNextStep() {
        cycle++;
        stepSize *= (1.0 - stepDampingFactor);
        iterationsPerCycle = (iterationsPerCycle * iterationIncreaseRate);
        divideEdges();
    }

    void divideEdges() {
        subdivisionPointsPerEdge *= subdivisionPointIncreaseRate;
        for (Edge edge : graphModel.getGraph().getEdges().toArray()) {
            FDEBUtilities.divideEdge(edge, subdivisionPointsPerEdge);
        }
    }

    @Override
    public void endAlgo() {
        super.endAlgo();
        executor.shutdown();
    }
}
