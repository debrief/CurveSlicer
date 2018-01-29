import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import junit.framework.TestCase;

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
      double brg = _bearingNormaliser.normalise(bearings.get(i));

      if (i % 12 == 0)
      {
        DecimalFormat df = new DecimalFormat("#.###");
        System.out.print(df.format(brg) + ", ");
      }

      normalTimes[i] = time;
      normalBearings[i] = brg;
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

    final ArrayList<LegIndex> pendingSlices = new ArrayList<LegIndex>();
    pendingSlices.add(new LegIndex(0, sampleCount - 1));

    while (!pendingSlices.isEmpty())
    {
      // get the next leg
      LegIndex nextPeriod = pendingSlices.get(0);
      processPeriod(nextPeriod, pendingSlices, normalTimes, normalBearings,
          optimizer);
    }

    System.out.println("best slice:" + bestSliceIndex);

    return null;
  }

  private void processPeriod(final LegIndex nextPeriod,
      final ArrayList<LegIndex> pendingSlices, final double[] normalTimes,
      final double[] normalBearings, final LMOptimizer optimizer)
  {
    // find the period of lowest bearing rate
    LegIndex lowRatePeriod =
        findLowRate(nextPeriod, normalTimes, normalBearings);

    if (lowRatePeriod == null)
    {
      // ok, can't be sliced. remove it from the list
      pendingSlices.remove(nextPeriod);
    }
    else
    {
      // ok, grow the start value
      boolean curveValid = true;
      for (int i = lowRatePeriod.start; i < nextPeriod.start; i--)
      {
        double fit = optimizer.scoreSlice(i, lowRatePeriod.end);

        // ok, see if we've still got a valid curve
        if (pointsAcceptable(optimizer.coeffs, i, lowRatePeriod.end,
            normalTimes, normalBearings))
        {
          // ok, we continue to walk left
        }
        else
        {
          // ok, remove the points until we get to the other side
        }

      }
    }
  }

  private static boolean pointsAcceptable(final double[] coeffs,
      final int start, final int end, final double[] normalTimes,
      final double[] normalBearings)
  {
    ArcTanFunction function = new ArcTanFunction();

    boolean leftEnd = checkThisEnd(coeffs, start, end, normalTimes, normalBearings,
        true, function);
    boolean rightEnd = checkThisEnd(coeffs, start, end, normalTimes, normalBearings,
        false, function);
    
    return leftEnd && rightEnd;
  }

  private static boolean checkThisEnd(final double[] coeffs, final int start,
      final int end, final double[] normalTimes, final double[] normalBearings,
      final boolean leftEnd, ArcTanFunction function)
  {
    final int windowLen = 3;
    ArrayWalker walker;
    if (leftEnd)
    {
      walker = new ArrayWalker(start + windowLen, start);
    }
    else
    {
      walker = new ArrayWalker(end - windowLen, end);
    }

    Boolean lastAbove = null;
    double lastError = Double.POSITIVE_INFINITY;

    while (walker.hasNext())
    {
      final int index = walker.next();

      double predicted = function.valueAt(normalTimes[index], coeffs);
      double actual = normalBearings[index];

      double error = predicted - actual;

      boolean thisAbove = error > 0;

      if (lastAbove == null)
      {
        lastAbove = thisAbove;
        lastError = Math.abs(error);
      }
      else
      {
        if (lastAbove != thisAbove)
        {
          // ok, switched side. drop out
          return true;
        }
        else
        {
          // check error growing
          if (error < lastError)
          {
            // error shrinking. we're ok
            return true;
          }
        }
      }
    }

    // ok, we've walked the data, and they're all on the same side

    return false;
  }

  private static class ArrayWalker implements Iterator<Integer>
  {
    private final int _start;
    private final int _end;
    private int _current;
    private final int _step;

    public ArrayWalker(int start, int end)
    {
      _start = start;
      _end = end;

      _current = end;
      _step = _end > _start ? 1 : -1;
    }

    @Override
    public void forEachRemaining(Consumer<? super Integer> arg0)
    {
      // TODO Auto-generated method stub

    }

    @Override
    public boolean hasNext()
    {
      return _current != _end;
    }

    @Override
    public Integer next()
    {
      _current += _step;
      return _current;
    }

    @Override
    public void remove()
    {
      // TODO Auto-generated method stub

    }
  }

  private LegIndex findLowRate(LegIndex nextPeriod, double[] normalTimes,
      double[] normalBearings)
  {
    if (normalTimes.length < 10)
    {
      return null;
    }
    // ok, walk through
    final int winSize = 5;

    int lowStartIndex = -1;
    double lowStartVal = Double.POSITIVE_INFINITY;

    for (int i = nextPeriod.start; i < nextPeriod.end - winSize; i++)
    {
      final double bDelta =
          Math.abs(normalBearings[i + winSize] - normalBearings[i]);
      final double tDelta = normalTimes[i + winSize] - normalTimes[i];
      final double rate = bDelta / tDelta;

      if (rate < lowStartVal)
      {
        lowStartIndex = i;
        lowStartVal = rate;
      }
    }

    if (lowStartIndex == -1)
    {
      return null;
    }
    else
    {
      return new LegIndex(lowStartIndex, lowStartIndex + winSize);
    }
  }

  private static class LegIndex
  {
    public final int start;
    public final int end;

    public LegIndex(final int start, final int end)
    {
      this.start = start;
      this.end = end;
    }
  }

  private static class LMOptimizer
  {
    private final double[] normalTimes;
    private final CurveFunction function;

    private final double[] normalBearings;

    private final double[] weights;

    private final double[] initialParameters;

    private final int maxIterations;
    private double[] coeffs;

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

        coeffs = opt.getBestFitParameters();

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

    public double[] getCoeffs()
    {
      return coeffs;
    }
  };

  public static class testMe extends TestCase
  {
    public void testCurveAcceptable() throws FileNotFoundException,
        IOException, ParseException
    {
      final double[] times = new double[]
      {0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
      final double[] values = new double[]
      {1, 0.977, 0.941, 0.876, 0.738, 0.421, 0.196, 0.099, 0.049, 0.02, 0};
      final double[] weights = new double[]
      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
      final ICurveSlicer.CurveFunction function = new ArcTanFunction();

      final double[] initialParameters = new double[]
      {1, 1, 1};
      LMOptimizer optimizer =
          new LMOptimizer(function, times, values, weights, initialParameters,
              10000);

      double score = optimizer.scoreSlice(0, times.length - 1);
      double[] coeffs = optimizer.getCoeffs();

      assertTrue("curve ok", pointsAcceptable(coeffs, 0, times.length - 1,
          times, values));

      // mangle some early values
      values[0] = 0.95;
      values[1] = 0.94;

      assertTrue("curve not ok", pointsAcceptable(coeffs, 0, times.length - 1,
          times, values));

    }
  }
}
