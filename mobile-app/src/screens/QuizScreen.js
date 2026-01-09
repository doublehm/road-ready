import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Alert, ActivityIndicator, Animated } from 'react-native';
import client from '../api/client';

const QuizScreen = () => {
  const [question, setQuestion] = useState(null);
  const [loading, setLoading] = useState(true);
  const [score, setScore] = useState(0);
  const [streak, setStreak] = useState(0);

  useEffect(() => {
    fetchQuestion();
  }, []);

  const fetchQuestion = async () => {
    setLoading(true);
    try {
      const response = await client.get('/quiz/random');
      setQuestion(response.data);
    } catch (e) {
      Alert.alert("Error", "Could not load question");
    } finally {
      setLoading(false);
    }
  };

  const handleAnswer = (selectedOption) => {
    if (selectedOption === question.correct_option) {
      Alert.alert("Correct! 🎉", question.explanation, [
        { text: "Next", onPress: () => {
            setScore(score + 1);
            setStreak(streak + 1);
            fetchQuestion();
        }}
      ]);
    } else {
      Alert.alert("Wrong ❌", `The correct answer was ${question.correct_option}. \n\n${question.explanation}`, [
         { text: "Next", onPress: () => {
             setStreak(0);
             fetchQuestion();
         }}
      ]);
    }
  };

  if (loading) return <ActivityIndicator style={styles.loader} size="large" color="#FF5864" />; 

  if (!question) return <View style={styles.container}><Text>No questions available.</Text></View>;

  return (
    <View style={styles.container}>
      <View style={styles.header}>
         <Text style={styles.score}>Score: {score}</Text>
         <Text style={styles.streak}>🔥 {streak}</Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.questionText}>{question.question_text}</Text>
        
        <View style={styles.optionsContainer}>
          {['A', 'B', 'C', 'D'].map((opt) => (
            <TouchableOpacity 
              key={opt} 
              style={styles.optionButton} 
              onPress={() => handleAnswer(opt)}
            >
              <Text style={styles.optionText}>
                <Text style={styles.optionLabel}>{opt}. </Text>
                {question[`option_${opt.toLowerCase()}`]}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f0f2f5', padding: 20, justifyContent: 'center' },
  loader: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 20, position: 'absolute', top: 50, left: 20, right: 20, zIndex: 1 },
  score: { fontSize: 20, fontWeight: 'bold', color: '#333' },
  streak: { fontSize: 20, fontWeight: 'bold', color: '#FF5864' },
  card: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.2,
    shadowRadius: 10,
    elevation: 10,
    minHeight: 400,
    justifyContent: 'center',
  },
  questionText: { fontSize: 22, fontWeight: 'bold', textAlign: 'center', marginBottom: 30, color: '#333' },
  optionsContainer: { width: '100%' },
  optionButton: {
    backgroundColor: '#f8f9fa',
    padding: 15,
    borderRadius: 10,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e9ecef',
  },
  optionText: { fontSize: 16, color: '#495057' },
  optionLabel: { fontWeight: 'bold', color: '#FF5864' },
});

export default QuizScreen;
