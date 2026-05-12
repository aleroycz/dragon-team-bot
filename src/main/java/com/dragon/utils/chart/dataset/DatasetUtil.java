package com.dragon.utils.chart.dataset;

/*
---------------------------------------------------------------------------------
File Name : DatasetUtil.java

Developer : vakea
Email     : vakea@fluffici.eu
Real Name : Alex Guy Yann Le Roy

Date Created  : 02/06/2024
Last Modified : 02/06/2024

---------------------------------------------------------------------------------
*/

import com.dragon.utils.chart.dataset.interfaces.Countable;
import com.dragon.utils.chart.dataset.interfaces.Summable;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("ALL")
public class DatasetUtil {

    /**
     * Counts the number of items in the dataset on a daily basis.
     *
     * @param <T>     the type of the elements in the dataset, must implement the Countable interface
     * @param dataset the dataset to count
     * @return a map where the keys are the dates and the values are the corresponding counts
     */
    public static <T extends Countable> Map<LocalDate, Long> countToDailyBasis(List<T> dataset, Predicate<T> filter) {
        if (dataset == null)
            throw new IllegalArgumentException("The 'dataset' argument cannot be null.");

        return dataset.stream()
                .filter(filter)
                .collect(Collectors.groupingBy(
                        item -> item.getCreatedAt().toLocalDateTime().toLocalDate(),
                        Collectors.counting()
                ));
    }

    /**
     * Calculates the ratio of join counts to left counts on a daily basis for a given dataset.
     *
     * @param <T>         the type of the elements in the dataset, must extend Countable
     * @param dataset     the dataset to calculate the ratio for
     * @param joinFilter  a predicate used to filter join elements from the dataset
     * @param leftFilter  a predicate used to filter left elements from the dataset
     * @return a map where the keys are the dates and the values are the corresponding ratios
     * @throws InvalidDatasetException if the dataset is null
     */
    public static <T extends Countable> Map<LocalDate, Double> ratioToDailyBasis(List<T> dataset, Predicate<T> joinFilter, Predicate<T> leftFilter) {
        if (dataset == null)
            throw new IllegalArgumentException("The 'dataset' argument cannot be null.");

        Map<LocalDate, Long> joinCounts = dataset.stream()
                .filter(joinFilter)
                .collect(Collectors.groupingBy(
                        item -> item.getCreatedAt().toLocalDateTime().toLocalDate(),
                        Collectors.counting()
                ));

        Map<LocalDate, Long> leftCounts = dataset.stream()
                .filter(leftFilter)
                .collect(Collectors.groupingBy(
                        item -> item.getCreatedAt().toLocalDateTime().toLocalDate(),
                        Collectors.counting()
                ));

        return joinCounts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            LocalDate date = entry.getKey();
                            long joinCount = entry.getValue();
                            long leftCount = leftCounts.getOrDefault(date, 0L);
                            return leftCount == 0 ? joinCount : (double) joinCount / leftCount;
                        }
                ));
    }

    /**
     * Sums the values of items in the dataset on a daily basis.
     *
     * @param <T> the type of the elements in the dataset, must implement the Summable interface
     * @param dataset the dataset to sum
     * @return a map where the keys are the dates and the values are the corresponding sums
     */
    public static <T extends Summable> Map<LocalDate, Long> sumToDailyBasis(List<T> dataset, Predicate<T> filter) {
        if (dataset == null)
            throw new IllegalArgumentException("The 'dataset' argument cannot be null.");

        return dataset.stream()
                .filter(filter)
                .collect(Collectors.groupingBy(
                        item -> item.getCreatedAt().toLocalDateTime().toLocalDate(),
                        Collectors.summingLong(Summable::getAmount)
                ));
    }

    /**
     * Counts the number of items in the dataset on a monthly basis.
     *
     * @param <T>     the type of the elements in the dataset, must implement the Countable interface
     * @param dataset the dataset to count
     * @return a map where the keys are the YearMonth objects and the values are the corresponding counts
     */
    public static <T extends Countable> Map<YearMonth, Long> countToMonthlyBasis(List<T> dataset, Predicate<T> filter) {
        if (dataset == null)
            throw new IllegalArgumentException("The 'dataset' argument cannot be null.");

        return dataset.stream()
                .filter(filter)
                .collect(Collectors.groupingBy(
                        item -> YearMonth.from(item.getCreatedAt().toLocalDateTime()),
                        Collectors.counting()
                ));
    }

    /**
     * Sums the values of items in the dataset on a monthly basis.
     *
     * @param <T> the type of the elements in the dataset, must implement the Summable interface
     * @param dataset the dataset to sum
     * @return a map where the keys are the YearMonth objects and the values are the corresponding sums
     *
     * @param dataset the dataset to sum
     * @return a map where the keys are the YearMonth objects and the values are the corresponding sums
     *
     * @throws NullPointerException if dataset is null
     */
    public static <T extends Summable> Map<YearMonth, Long> sumToMonthlyBasis(List<T> dataset, Predicate<T> filter) {
        if (dataset == null)
            throw new IllegalArgumentException("The 'dataset' argument cannot be null.");

        return dataset.stream()
                .filter(filter)
                .collect(Collectors.groupingBy(
                        item -> YearMonth.from(item.getCreatedAt().toLocalDateTime()),
                        Collectors.summingLong(Summable::getAmount)
                ));
    }

    /**
     * Counts the number of items in the dataset on a yearly basis.
     *
     * @param <T>     the type of the elements in the dataset, must implement the Countable interface
     * @param dataset the dataset to count
     * @return a map where the keys are the Year objects and the values are the corresponding counts
     * @throws NullPointerException if dataset is null
     */
    public static <T extends Countable> Map<Year, Long> countToYearlyBasis(List<T> dataset, Predicate<T> filter) {
        if (dataset == null)
            throw new IllegalArgumentException("The 'dataset' argument cannot be null.");

        return dataset.stream()
                .filter(filter)
                .collect(Collectors.groupingBy(
                        item -> Year.from(item.getCreatedAt().toLocalDateTime()),
                        Collectors.counting()
                ));
    }

    /**
     * Sums the values of items in the dataset on a yearly basis.
     *
     * @param <T>     the type of the elements in the dataset, must implement the Summable interface
     * @param dataset the dataset to sum
     * @return a map where the keys are the Year objects and the values are the corresponding sums
     * @throws NullPointerException if dataset is null
     */
    public static <T extends Summable> Map<Year, Long> sumToYearlyBasis(List<T> dataset, Predicate<T> filter) {
        if (dataset == null)
            throw new IllegalArgumentException("The 'dataset' argument cannot be null.");

        return dataset.stream()
                .filter(filter)
                .collect(Collectors.groupingBy(
                        item -> Year.from(item.getCreatedAt().toLocalDateTime()),
                        Collectors.summingLong(Summable::getAmount)
                ));
    }

    /**
     * Counts the number of items in the dataset on a trimester basis.
     *
     * @param <T>     the type of the elements in the dataset, must implement the Countable interface
     * @param dataset the dataset to count
     * @return a map where the keys are the YearMonth objects representing the trimesters and the values are the corresponding counts
     */
    public static <T extends Countable> Map<YearMonth, Long> countToTrimesterBasis(List<T> dataset, Predicate<T> filter) {
        if (dataset == null)
            throw new IllegalArgumentException("The 'dataset' argument cannot be null.");

        return dataset.stream()
                .filter(filter)
                .collect(Collectors.groupingBy(
                        item -> getTrimester(item.getCreatedAt().toLocalDateTime().getMonthValue()),
                        Collectors.counting()
                ));
    }

    /**
     * Sums the values of items in the dataset on a trimester basis.
     *
     * @param <T>     the type of the elements in the dataset, must implement the Summable interface
     * @param dataset the dataset to sum
     * @return a map where the keys are the YearMonth objects representing the trimesters and the values are the corresponding sums
     * @throws NullPointerException if dataset is null
     */
    public static <T extends Summable> Map<YearMonth, Long> sumToTrimesterBasis(List<T> dataset, Predicate<T> filter) {
        if (dataset == null)
            throw new IllegalArgumentException("The 'dataset' argument cannot be null.");

        return dataset.stream()
                .filter(filter)
                .collect(Collectors.groupingBy(
                        item -> getTrimester(item.getCreatedAt().toLocalDateTime().getMonthValue()),
                        Collectors.summingLong(Summable::getAmount)
                ));
    }

    /**
     * Returns the YearMonth corresponding to the trimester of the given month.
     *
     * @param month the month for which to get the trimester (1-12)
     * @return the YearMonth corresponding to the trimester of the given month
     * @throws IllegalArgumentException if the month is invalid (not in the range 1-12)
     */
    private static YearMonth getTrimester(int month) {
        int trimester = (month - 1) / 3 + 1;
        int year = Year.now().getValue();

        return switch (trimester) {
            case 1 -> YearMonth.of(year, 1);
            case 2 -> YearMonth.of(year, 4);
            case 3 -> YearMonth.of(year, 7);
            case 4 -> YearMonth.of(year, 10);
            default -> throw new IllegalArgumentException("Invalid trimester: " + trimester);
        };
    }
}
