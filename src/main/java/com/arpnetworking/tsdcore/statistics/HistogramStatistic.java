/*
 * Copyright 2015 Groupon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.tsdcore.statistics;

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.metrics.mad.model.DefaultQuantity;
import com.arpnetworking.metrics.mad.model.Quantity;
import com.arpnetworking.metrics.mad.model.Unit;
import com.arpnetworking.tsdcore.model.CalculatedValue;
import it.unimi.dsi.fastutil.doubles.Double2IntAVLTreeMap;
import it.unimi.dsi.fastutil.doubles.Double2IntMap;
import it.unimi.dsi.fastutil.doubles.Double2IntSortedMap;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import net.sf.oval.constraint.NotNull;

import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Histogram statistic. This is a supporting statistic and does not produce
 * a value itself. It is used by percentile statistics as a common dependency.
 * Use <code>StatisticFactory</code> for construction.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class HistogramStatistic extends BaseStatistic {

    @Override
    public String getName() {
        return "histogram";
    }

    @Override
    public Accumulator<HistogramSupportingData> createCalculator() {
        return new HistogramAccumulator(this);
    }

    private HistogramStatistic() { }

    private static final long serialVersionUID = 7060886488604176233L;

    /**
     * Accumulator computing the histogram of values. There is a dependency on the
     * histogram accumulator from each percentile statistic's calculator.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    /* package private */ static final class HistogramAccumulator
            extends BaseCalculator<HistogramSupportingData>
            implements Accumulator<HistogramSupportingData> {

        /**
         * Public constructor.
         *
         * @param statistic The <code>Statistic</code>.
         */
        /* package private */ HistogramAccumulator(final Statistic statistic) {
            super(statistic);
        }

        @Override
        public Accumulator<HistogramSupportingData> accumulate(final Quantity quantity) {
            // Assert: that under the new Quantity normalization the units should always be the same.
            assertUnit(_unit, quantity.getUnit(), _histogram._entriesCount > 0);

            _histogram.recordValue(quantity.getValue());
            _unit = Optional.ofNullable(_unit.orElse(quantity.getUnit().orElse(null)));

            return this;
        }

        @Override
        public Accumulator<HistogramSupportingData> accumulate(final CalculatedValue<HistogramSupportingData> calculatedValue) {
            // Assert: that under the new Quantity normalization the units should always be the same.
            assertUnit(_unit, calculatedValue.getData().getUnit(), _histogram._entriesCount > 0);

            _histogram.add(calculatedValue.getData().getHistogramSnapshot());
            _unit = Optional.ofNullable(_unit.orElse(calculatedValue.getData().getUnit().orElse(null)));

            return this;
        }

        @Override
        public CalculatedValue<HistogramSupportingData> calculate(final Map<Statistic, Calculator<?>> dependencies) {
            return ThreadLocalBuilder.<
                    CalculatedValue<HistogramSupportingData>,
                    CalculatedValue.Builder<HistogramSupportingData>>buildGeneric(
                            CalculatedValue.Builder.class,
                            b1 -> b1.setValue(
                                    ThreadLocalBuilder.build(
                                            DefaultQuantity.Builder.class,
                                            b2 -> b2.setValue(1.0)))
                                    .setData(
                                            ThreadLocalBuilder.build(
                                                    HistogramSupportingData.Builder.class,
                                                    builder -> builder.setHistogramSnapshot(_histogram.getSnapshot())
                                                            .setUnit(_unit.orElse(null)))));
        }

        /**
         * Calculate the value at the specified percentile.
         *
         * @param percentile The desired percentile to calculate.
         * @return The value at the desired percentile.
         */
        public Quantity calculate(final double percentile) {
            final HistogramSnapshot snapshot = _histogram.getSnapshot();
            return ThreadLocalBuilder.build(
                    DefaultQuantity.Builder.class,
                    b -> b.setValue(snapshot.getValueAtPercentile(percentile))
                            .setUnit(_unit.orElse(null)));
        }

        private Optional<Unit> _unit = Optional.empty();
        private final Histogram _histogram = new Histogram();
    }

    /**
     * Supporting data based on a histogram.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
     */
    public static final class HistogramSupportingData {
        /**
         * Public constructor.
         *
         * @param builder The builder.
         */
        public HistogramSupportingData(final Builder builder) {
            _unit = Optional.ofNullable(builder._unit);
            _histogramSnapshot = builder._histogramSnapshot;
        }

        public HistogramSnapshot getHistogramSnapshot() {
            return _histogramSnapshot;
        }

        /**
         * Transforms the histogram to a new unit. If there is no unit set,
         * the result is a no-op.
         *
         * @param newUnit the new unit
         * @return a new HistogramSupportingData with the units converted
         */
        public HistogramSupportingData toUnit(final Unit newUnit) {
            if (_unit.isPresent()) {
                final Histogram newHistogram = new Histogram();
                for (final Map.Entry<Double, Integer> entry : _histogramSnapshot.getValues()) {
                    final Double newBucket = newUnit.convert(entry.getKey(), _unit.get());
                    newHistogram.recordValue(newBucket, entry.getValue());
                }
                return ThreadLocalBuilder.build(
                        HistogramSupportingData.Builder.class,
                        builder -> builder.setHistogramSnapshot(newHistogram.getSnapshot())
                                .setUnit(newUnit));
            }
            return this;
        }

        public Optional<Unit> getUnit() {
            return _unit;
        }

        private final Optional<Unit> _unit;
        private final HistogramSnapshot _histogramSnapshot;

        /**
         * Implementation of the builder pattern for a {@link HistogramSupportingData}.
         *
         * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
         */
        public static class Builder extends ThreadLocalBuilder<HistogramSupportingData> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(HistogramSupportingData::new);
            }

            /**
             * Sets the histogram. Required. Cannot be null.
             *
             * @param value the histogram
             * @return This {@link Builder} instance.
             */
            public Builder setHistogramSnapshot(final HistogramSnapshot value) {
                _histogramSnapshot = value;
                return this;
            }

            /**
             * Sets the unit. Optional. Cannot be null.
             *
             * @param value the unit
             * @return This {@link Builder} instance.
             */
            public Builder setUnit(@Nullable final Unit value) {
                _unit = value;
                return this;
            }

            @Override
            protected void reset() {
                _unit = null;
                _histogramSnapshot = null;
            }

            private Unit _unit;
            @NotNull
            private HistogramSnapshot _histogramSnapshot;
        }
    }

    /**
     * A simple histogram implementation.
     */
    public static final class Histogram {

        /**
         * Records a value into the histogram.
         *
         * @param value The value of the entry.
         * @param count The number of entries at this value.
         */
        public void recordValue(final double value, final int count) {
            _data.merge(truncate(value), count, (i, j) -> i + j);
            _entriesCount += count;
        }

        /**
         * Records a value into the histogram.
         *
         * @param value The value of the entry.
         */
        public void recordValue(final double value) {
            recordValue(value, 1);
        }

        /**
         * Adds a histogram snapshot to this one.
         *
         * @param histogramSnapshot The histogram snapshot to add to this one.
         */
        public void add(final HistogramSnapshot histogramSnapshot) {
            for (final Double2IntMap.Entry entry : histogramSnapshot._data.double2IntEntrySet()) {
                _data.merge(entry.getDoubleKey(), entry.getIntValue(), (i, j) -> i + j);
            }
            _entriesCount += histogramSnapshot._entriesCount;
        }

        public HistogramSnapshot getSnapshot() {
            return new HistogramSnapshot(_data, _entriesCount);
        }

        private static double truncate(final double val) {
            final long mask = 0xffffe00000000000L;
            return Double.longBitsToDouble(Double.doubleToRawLongBits(val) & mask);
        }

        private int _entriesCount = 0;
        private final Double2IntSortedMap _data = new Double2IntAVLTreeMap();
    }

    /**
     * Represents a snapshot of immutable histogram data.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
     */
    public static final class HistogramSnapshot {
        private HistogramSnapshot(final Double2IntSortedMap data, final int entriesCount) {
            _entriesCount = entriesCount;
            _data.putAll(data);
        }

        /**
         * Gets the value of the bucket that corresponds to the percentile.
         *
         * @param percentile the percentile
         * @return The value of the bucket at the percentile.
         */
        public Double getValueAtPercentile(final double percentile) {
            // Always "round up" on fractional samples to bias toward 100%
            // The Math.min is for the case where the computation may be just
            // slightly larger than the _entriesCount and prevents an index out of range.
            final int target = (int) Math.min(Math.ceil(_entriesCount * percentile / 100.0D), _entriesCount);
            int accumulated = 0;
            for (final Double2IntMap.Entry next : _data.double2IntEntrySet()) {
                accumulated += next.getIntValue();
                if (accumulated >= target) {
                    return next.getDoubleKey();
                }
            }
            return 0D;
        }

        public int getEntriesCount() {
            return _entriesCount;
        }

        public ObjectSortedSet<Double2IntMap.Entry> getValues() {
            return _data.double2IntEntrySet();
        }

        private int _entriesCount = 0;
        private final Double2IntSortedMap _data = new Double2IntAVLTreeMap();
    }
}
