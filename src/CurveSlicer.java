import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.finmath.optimizer.LevenbergMarquardt;
import net.finmath.optimizer.SolverException;

public class CurveSlicer implements ICurveSlicer
{

  private final List<Long> times;
  private final List<Double> bearings;

  public CurveSlicer(final List<Long> timeStamps, final List<Double> values)
  {
    this.times = timeStamps;
    this.bearings = values;
  }

  /** the start/end indices of a period of time
   * 
   * @author Ian
   *
   */
  private static class TPeriod implements Comparable<TPeriod>
  {
    public int start;
    public int end;

    public TPeriod(final int start, final int end)
    {
      this.start = start;
      this.end = end;
    }

    @Override
    public int compareTo(final TPeriod other)
    {
      return Integer.compare(start, other.start);
    }

    @Override
    public boolean equals(final Object arg0)
    {
      if (arg0 instanceof TPeriod)
      {
        final TPeriod other = (TPeriod) arg0;
        return other.start == start && other.end == end;
      }
      else
      {
        return false;
      }
    }

    @Override
    public int hashCode()
    {
      return start * 10000 + end;
    }

    @Override
    public String toString()
    {
      return "Period:" + start + "-" + end;
    }

    public String toString(List<Long> thisTimes)
    {

      if (start >= thisTimes.size() || end >= thisTimes.size() || start == -1
          || end == -1)
      {
        System.out.println("Trouble");
        return "Period:" + start + "-" + end + ", " + thisTimes.size();// + thisTimes.get(start) +
                                                                       // "secs- " +
                                                                       // thisTimes.get(end) +
                                                                       // "secs";
      }
      return "Period:" + start + "-" + end + ", " + thisTimes.get(start)
          + "secs- " + thisTimes.get(end) + "secs";
    }

  }

  /**
   * find the period of data with the lowest rate of change of rate of change of bearing (the
   * smoothest part of the curve)
   * 
   * @param legTimes
   * @param legBearings
   * @return
   */
  private static TPeriod findLowestRateIn(final List<Long> legTimes,
      final List<Double> legBearings)
  {
    int window;
    final int len = legTimes.size();
    if (len < 20)
    {
      window = 5;
    }
    else if (len < 40)
    {
      window = 7;
    }
    else
    {
      window = 12;
    }

    final TPeriod res;
    if (legTimes.size() <= window)
    {
      res = null;
    }
    else
    {
      int lowestStart = -1;
      double lowestRate = Double.POSITIVE_INFINITY;

      for (int i = 0; i < legTimes.size() - window; i++)
      {
        // find the sum of the bearing changes in this time period
        double runningSum = 0;
        Double lastB = null;
        for (int j = i + 1; j <= i + window; j++)
        {
          double bDelta = Math.abs(legBearings.get(j) - legBearings.get(j - 1));

          // check for passing through 360
          if (bDelta > 180)
          {
            bDelta = Math.abs(bDelta - 360d);
          }

          if (lastB != null)
          {
            double bDelta2 = Math.abs(lastB - bDelta);
            double tDelta = legTimes.get(j) - legTimes.get(j - 1);
            double bDelta2Rate = bDelta2 / tDelta;

            runningSum += (bDelta2Rate);
          }
          lastB = bDelta;

        }

        // System.out.println(legTimes.get(i) + ", " + runningSum);

        if (runningSum < lowestRate)
        {
          lowestStart = i;
          lowestRate = runningSum;
        }
      }
      res = new TPeriod(lowestStart, lowestStart + window);
    }
    return res;
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
    final double tStart = dTimes.get(0);

    // ok, collate the data
    final double[] normalTimes = new double[sampleCount];
    final double[] normalBearings = new double[sampleCount];
    final double[] weights = new double[sampleCount];

    for (int i = 0; i < sampleCount; i++)
    {
      normalTimes[i] = dTimes.get(i) - tStart;
      normalBearings[i] = bearings.get(i);
      weights[i] = 1d;
    }

    // Set solver parameters
    double[] initialParameters = new double[]
    {bearings.get(0), 1, 1};

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
