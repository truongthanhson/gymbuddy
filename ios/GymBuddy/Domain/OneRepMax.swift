/// Estimated one-rep max using the Epley formula.
/// Must match shared/test-vectors/one_rep_max.json (and the Android implementation).
func estimatedOneRepMax(weightKg: Double, reps: Int) -> Double {
    if reps <= 0 { return 0 }
    if reps == 1 { return weightKg }
    return weightKg * (1 + Double(reps) / 30)
}
