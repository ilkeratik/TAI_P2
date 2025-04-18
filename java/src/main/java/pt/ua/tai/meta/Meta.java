package pt.ua.tai.meta;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Meta {
    private final Logger log = Logger.getLogger(getClass().getName());
    private final Map<String, CharCounts> frequencyTable = new HashMap<>();
    private final Set<Character> alphabet = new LinkedHashSet<>();
    private final int k;
    private final String content;

    public Meta(String content, int k) {
        this.content = content.replaceAll("[^ATCG]", "");
        this.k = k;
        init();
    }

    public void init() {
        generateAlphabetSet();
        generateFrequencyTable(k);
    }

    public Map<String, Double> getBestSequences(Map<String, String> db, float alpha, int n) {
        return batchRunMultiThreaded(db, alpha).entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(n).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String, Double> batchRunMultiThreaded(Map<String, String> db, float alpha) {
        int numThreads = Math.max(1,Math.min(db.size() / 10, Runtime.getRuntime().availableProcessors() - 1)); // Dynamic thread pool size
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CompletionService<Map.Entry<String, Double>> completionService = new ExecutorCompletionService<>(executor);
        try {
            for (Map.Entry<String, String> entry : db.entrySet()) {
                completionService.submit(() -> {
                    double result = nrc(estimateTotalBits(entry.getValue(), alpha), entry.getValue());
                    return Map.entry(entry.getKey(), result);
                });
            }

            Map<String, Double> results = new HashMap<>(db.size(), 1);
            for (int i = 0; i < db.size(); i++) {
                try {
                    Future<Map.Entry<String, Double>> future = completionService.take(); // Wait for next completed task
                    Map.Entry<String, Double> resultEntry = future.get();
                    results.put(resultEntry.getKey(), resultEntry.getValue());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.log(Level.SEVERE, "Thread was interrupted", e);
                } catch (ExecutionException e) {
                    log.log(Level.SEVERE, "Error processing task ", e);
                }
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    public Map<String, Double> batchRun(Map<String, String> db, float alpha) {
        return db.entrySet().parallelStream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        it -> nrc(estimateTotalBits(it.getValue(), alpha), it.getValue())
                ));
    }

    public double nrc(double bits, String sequence) {
        final int sequenceLen = sequence.length();
        final String uniqueStr = sequence.replaceAll("(.)(?=.*?\\1)", "");
        final int unique = uniqueStr.length();
        return bits / (sequenceLen * log2(unique));
    }

    public double nrc2(double bits, int sequenceLen) {
        if(bits<0){
            System.err.println("negative bits:" + bits);
        }
        final int unique = 4;
        return bits / (sequenceLen * log2(unique));
    }

    private double log2(double logNumber) {
        return Math.log(logNumber) / Math.log(2);
    }

    public double estimateTotalBits(String sequence, float alpha) {
        final float alphaTimesAlphabet = alpha * alphabet.size();
        double totalSum = 0.0F;

        StringBuilder contextBuilder = new StringBuilder(sequence.substring(0, k));
        final int sequenceLength = sequence.length();

        for (int i = 0; i + k < sequenceLength; i++) {
            final String context = contextBuilder.toString();
            final char nextChar = sequence.charAt(i + k);
            CharCounts charCounts = frequencyTable.getOrDefault(context, new CharCounts());
            float symbolBits = getSymbolBits(charCounts, nextChar, alpha, alphaTimesAlphabet);
            totalSum += symbolBits;
            if (i + k + 1 < sequenceLength) {
                contextBuilder.deleteCharAt(0).append(sequence.charAt(i + k));
            }
        }
        return -totalSum / Math.log(2);
    }

    private float getSymbolBits(CharCounts charCounts, char nextChar, float alpha, float alphaTimesAlphabet) {
        final int contextTotalCount = charCounts.getTotalCount();
        final float denominator = contextTotalCount + alphaTimesAlphabet;
        final float probability = (charCounts.get(nextChar) + alpha) / denominator;
        return (float) Math.log(probability);
    }

    /**
     * Creates the context and succeeding character and count Map
     *
     * @param k context width
     */
    private void generateFrequencyTable(int k) {
        final int contentLength = content.length();
        StringBuilder contextBuilder = new StringBuilder(content.substring(0, k));
        for (int i = 0; i + k < content.length(); i++) {
            final String context = contextBuilder.toString();
            final char nextChar = content.charAt(i + k);
            CharCounts charCounts = frequencyTable.computeIfAbsent(context, key -> new CharCounts());
            charCounts.increment(nextChar);
            if (i + k + 1 < contentLength) {
                contextBuilder.deleteCharAt(0).append(content.charAt(i + k));
            }
        }
    }

    /**
     * Generate an alphabet from the symbols used in the content
     */
    private void generateAlphabetSet() {
        for (int i = 0; i < content.length(); i++) {
            alphabet.add(content.charAt(i));
        }
    }


    public List<Double> getNRCProgression(String sequence, float alpha) {
        List<Double> progression = new ArrayList<>();
        int aux = k;
        List<Double> bitsList = estimateBitsPerCharacter(sequence, alpha);

        for (double bits : bitsList) {
            if (aux > sequence.length()) {
                System.out.println("Breaking loop, aux exceeds sequence length.");
                break;
            }

            // Process the character directly instead of using substring
            progression.add(nrc2(bits, aux));  // Use charAt to access the character
            aux++;
        }

        return progression;
    }



    public List<Double> estimateBitsPerCharacter(String sequence, float alpha) {
        final float alphaTimesAlphabet = alpha * alphabet.size();
        double totalSum = 0.0F;
        List<Double> bitsList = new ArrayList<>();

        StringBuilder contextBuilder = new StringBuilder(sequence.substring(0, k));
        final int sequenceLength = sequence.length();

        for (int i = 0; i + k < sequenceLength; i++) {
            final String context = contextBuilder.toString();
            final char nextChar = sequence.charAt(i + k);
            CharCounts charCounts = frequencyTable.getOrDefault(context, new CharCounts());
            float symbolBits = getSymbolBits(charCounts, nextChar, alpha, alphaTimesAlphabet);
            totalSum += symbolBits;
            if (i + k + 1 < sequenceLength) {
                contextBuilder.deleteCharAt(0).append(sequence.charAt(i + k));
                bitsList.add(-symbolBits / Math.log(2));
            }
        }
        return bitsList;
    }
}
