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



        



        // Ensure unique questions by ID just in case



        const uniqueQuestions = Array.from(new Map(response.data.map(q => [q.id, q])).values());



        



        setQuestions(uniqueQuestions);



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



    const imageUrl = `${serverUrl}/static/${question.image_path}`;



    if (question.image_path) {



      console.log("Loading Quiz Image:", imageUrl);



    }



  



    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <View style={styles.headerTop}>
            <Text style={styles.progressText}>QUESTION {currentIndex + 1}/{questions.length}</Text>
            <Text style={styles.scoreText}>SCORE: {score}</Text>
          </View>
          <View style={styles.progressTrack}>
            <View style={[styles.progressFill, { width: `${((currentIndex + 1) / questions.length) * 100}%` }]} />
          </View>
        </View>

        <View style={styles.card}>
          {question.image_path && (
            <Image 
              key={question.image_path}
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
                <View style={styles.optionLabelContainer}>
                  <Text style={styles.optionLabel}>{opt}</Text>
                </View>
                <Text style={styles.optionText}>
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
    container: { flex: 1, backgroundColor: '#F6FAFE', padding: 20, justifyContent: 'center' },
    loader: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#F6FAFE' },
    header: { position: 'absolute', top: 60, left: 20, right: 20, zIndex: 1 },
    headerTop: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 },
    progressText: { fontSize: 12, fontWeight: '800', color: '#64748B', letterSpacing: 1 },
    scoreText: { fontSize: 12, fontWeight: '800', color: '#15803D', letterSpacing: 1 },
    progressTrack: { height: 8, backgroundColor: '#E2E8F0', borderRadius: 4, overflow: 'hidden' },
    progressFill: { height: '100%', backgroundColor: '#15803D', borderRadius: 4 },

    finishTitle: { fontSize: 32, fontWeight: '800', color: '#1E293B', textAlign: 'center', marginBottom: 12, letterSpacing: -1 },
    finishScore: { fontSize: 18, color: '#64748B', textAlign: 'center', marginBottom: 40, fontWeight: '600' },
    finishCard: { backgroundColor: 'white', borderRadius: 24, padding: 32, alignItems: 'center', shadowColor: '#1E293B', shadowOpacity: 0.1, shadowRadius: 20, elevation: 5 },

    card: {
      backgroundColor: 'white',
      borderRadius: 24,
      padding: 24,
      shadowColor: '#1E293B',
      shadowOpacity: 0.06,
      shadowRadius: 20,
      elevation: 4,
      minHeight: 450,
    },
    questionImage: {
      width: '100%',
      height: 180,
      borderRadius: 16,
      marginBottom: 24,
      backgroundColor: '#F8FAFC',
    },
    questionText: { fontSize: 22, fontWeight: '800', textAlign: 'center', marginBottom: 32, color: '#1E293B', letterSpacing: -0.5, lineHeight: 28 },
    optionsContainer: { width: '100%' },
    optionButton: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: '#F8FAFC',
      padding: 16,
      borderRadius: 16,
      marginBottom: 12,
    },
    optionLabelContainer: {
      width: 32,
      height: 32,
      borderRadius: 8,
      backgroundColor: '#1E293B',
      justifyContent: 'center',
      alignItems: 'center',
      marginRight: 16,
    },
    optionLabel: { fontWeight: '800', color: 'white', fontSize: 14 },
    optionText: { fontSize: 16, color: '#1E293B', fontWeight: '600', flex: 1 },
    });

export default QuizScreen;
