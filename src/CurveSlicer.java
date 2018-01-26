import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.finmath.optimizer.LevenbergMarquardt;
import net.finmath.optimizer.SolverException;

public class CurveSlicer implements ICurveSlicer
{

  private final List<Long> times;
  private final List<Double> bearings;
  private Normaliser _timeNormaliser;
  private Normaliser _bearingNormaliser;

  public CurveSlicer(final List<Long> timeStamps, final List<Double> values)
  {
    this.times = timeStamps;
    this.bearings = values;
  }

  @Override
  public List<Zone> getZones(final CurveFunction function, final double fit,
      final long minLeg)
  {
    // do some data testing
    if (times == null || bearings == null)
    {
      throw new IllegalArgumentException("The input datasets cannot be null");
    }

    if (times.size() == 0 || bearings.size() == 0)
    {
      throw new IllegalArgumentException("The input datasets cannot be empty");
    }

    final int sampleCount = times.size();

    // convert the times to doubles
    ArrayList<Double> dTimes = new ArrayList<Double>();
    for (Long t : times)
    {
      dTimes.add((double) t);
    }

    // create the normaliser for the two datasets
    _timeNormaliser = new Normaliser(dTimes, false);
    _bearingNormaliser = new Normaliser(bearings, true);

    // ok, collate the data
    final double[] normalTimes = new double[sampleCount];
    final double[] normalBearings = new double[sampleCount];
    final double[] weights = new double[sampleCount];

    for (int i = 0; i < sampleCount; i++)
    {
      double time = _timeNormaliser.normalise(dTimes.get(i));
      double freq = _bearingNormaliser.normalise(bearings.get(i));

      normalTimes[i] = time;
      normalBearings[i] = freq;
      weights[i] = 1d;
    }

    // Set solver parameters
    final double initialBearing = _bearingNormaliser.normalise(bearings.get(0));
    double[] initialParameters = new double[]
    {initialBearing, 1, 1};

    // ok, now create our optimizer
    LMOptimizer optimizer =
        new LMOptimizer(function, normalTimes, normalBearings, weights,
            initialParameters, 10000);

    // work out the overall fit
    final double initialFit = optimizer.scoreSlice(0, sampleCount - 1);
    System.out.println("Overall:" + initialFit);

    double bestSliceScore = Double.POSITIVE_INFINITY;
    int bestSliceIndex = -1;

    for (int sliceHere = 0; sliceHere < sampleCount; sliceHere++)
    {
      // ok, now we experiment with fits

      // try some random permutation
      // final int sliceHere = (int) (Math.random() * sampleCount);

      final int beforeLegStart = 0;
      final int beforeLegEnd = sliceHere;

      final int afterLegStart = sliceHere + 1;
      final int afterLegEnd = sampleCount - 1;

      final double beforeFit =
          optimizer.scoreSlice(beforeLegStart, beforeLegEnd);
      final double afterFit = optimizer.scoreSlice(afterLegStart, afterLegEnd);

      final double overallScore = beforeFit + afterFit;

      if (overallScore < bestSliceScore)
      {
        bestSliceIndex = sliceHere;
      }

      System.out.println(sliceHere + "," + beforeFit + ", " + afterFit + ", "
          + (beforeFit + afterFit) + ", " + bearings.get(sliceHere));

    }

    System.out.println("best slice:" + bestSliceIndex);

    return null;
  }

  private static class LMOptimizer
  {
    private final double[] normalTimes;
    private final CurveFunction function;

    private final double[] normalBearings;

    private final double[] weights;

    private final double[] initialParameters;

    private final int maxIterations;

    public LMOptimizer(final CurveFunction function,
        final double[] normalTimes, double[] normalBearings, double[] weights,
        double[] initialParameters, int maxIterations)
    {
      this.normalTimes = normalTimes;
      this.normalBearings = normalBearings;
      this.weights = weights;
      this.initialParameters = initialParameters;
      this.function = function;
      this.maxIterations = maxIterations;
    }

    /**
     * find the RMS error in fitting the slice to this period of data
     * 
     * @param start
     *          index of first point
     * @param end
     *          index of end point
     * @return
     */
    public double scoreSlice(final int start, final int end)
    {
      // check the data
      if (end < start + 4)
      {
        return Double.POSITIVE_INFINITY;
      }

      // store the trimmed set of times
      final double[] currentTimes = Arrays.copyOfRange(normalTimes, start, end);

      LevenbergMarquardt opt = new LevenbergMarquardt()
      {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        @Override
        public void setValues(double[] parameters, double[] values)
            throws SolverException
        {
          for (int i = 0; i < currentTimes.length; i++)
          {
            final double thisT = currentTimes[i];
            values[i] = function.valueAt(thisT, values);
          }
        }
      };

      // setup the optimiser
      double[] trimmedBearings = Arrays.copyOfRange(normalBearings, start, end);
      initialParameters[0] = trimmedBearings[0];
      opt.setInitialParameters(initialParameters);
      opt.setWeights(Arrays.copyOfRange(weights, start, end));
      opt.setMaxIteration(maxIterations);
      opt.setTargetValues(Arrays.copyOfRange(normalBearings, start, end));

      // ok, now run the optimiser
      double res = Double.POSITIVE_INFINITY;
      try
      {
        opt.run();

        final double error = opt.getRootMeanSquaredError();

        // normalise the error
        res = error / (end - start);
      }
      catch (SolverException e)
      {
        e.printStackTrace();
      }

      // and return the score
      return res;

    }
  };
}
