import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Alert, ActivityIndicator, Animated, Image } from 'react-native';
import client from '../api/client';

const QuizScreen = () => {

  const [questions, setQuestions] = useState([]);

  const [currentIndex, setCurrentIndex] = useState(0);

  const [loading, setLoading] = useState(true);

  const [score, setScore] = useState(0);

  const [finished, setFinished] = useState(false);



  // Get the base server URL (without /api/v1) for static images

  const serverUrl = client.defaults.baseURL.replace('/api/v1', '');



  useEffect(() => {

    fetchQuizSet();

  }, []);



  const fetchQuizSet = async () => {

    setLoading(true);

    try {

      const response = await client.get('/quiz/set?count=10');

      setQuestions(response.data);

      setCurrentIndex(0);

      setScore(0);

      setFinished(false);

    } catch (e) {

      Alert.alert("Error", "Could not load quiz");

    } finally {

      setLoading(false);

    }

  };



  const handleAnswer = (selectedOption) => {

    const question = questions[currentIndex];

    const isCorrect = selectedOption === question.correct_option;



    if (isCorrect) {

      setScore(score + 1);

      Alert.alert("Correct! 🎉", question.explanation, [

        { text: "Next", onPress: nextQuestion }

      ]);

    } else {

      Alert.alert("Wrong ❌", `The correct answer was ${question.correct_option}. \n\n${question.explanation}`, [

         { text: "Next", onPress: nextQuestion }

      ]);

    }

  };



  const nextQuestion = () => {

    if (currentIndex + 1 < questions.length) {

      setCurrentIndex(currentIndex + 1);

    } else {

      setFinished(true);

    }

  };



  if (loading) return <ActivityIndicator style={styles.loader} size="large" color="#FF5864" />; 



  if (finished) {

    return (

      <View style={styles.container}>

        <View style={styles.card}>

          <Text style={styles.finishTitle}>Quiz Complete!</Text>

          <Text style={styles.finishScore}>You scored {score} out of {questions.length}</Text>

          <TouchableOpacity style={styles.optionButton} onPress={fetchQuizSet}>

            <Text style={[styles.optionText, {textAlign: 'center'}]}>Try Another Quiz</Text>

          </TouchableOpacity>

        </View>

      </View>

    );

  }



  if (questions.length === 0) return <View style={styles.container}><Text>No questions available.</Text></View>;



  const question = questions[currentIndex];



  return (

    <View style={styles.container}>

      <View style={styles.header}>

         <Text style={styles.score}>Question: {currentIndex + 1}/{questions.length}</Text>

         <Text style={styles.streak}>Score: {score}</Text>

      </View>



      <View style={styles.card}>

        {question.image_path && (

          <Image 

            source={{ uri: `${serverUrl}/static/${question.image_path}` }}

            style={styles.questionImage}

            resizeMode="contain"

          />

        )}

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
  finishTitle: { fontSize: 32, fontWeight: 'bold', color: '#333', textAlign: 'center', marginBottom: 20 },
  finishScore: { fontSize: 24, color: '#666', textAlign: 'center', marginBottom: 40 },
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
  questionImage: {
    width: '100%',
    height: 200,
    borderRadius: 10,
    marginBottom: 20,
    backgroundColor: '#fff',
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
