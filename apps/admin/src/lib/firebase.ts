import { initializeApp } from "firebase/app";
import { getAuth, connectAuthEmulator } from "firebase/auth";
import { getFirestore, connectFirestoreEmulator } from "firebase/firestore";
import { getFunctions, connectFunctionsEmulator } from "firebase/functions";

// Production Firebase Web App Configuration for managing-screen-time
const firebaseConfig = {
  projectId: "managing-screen-time",
  appId: "1:54296812917:web:7d7e622da26c57b2a4473c",
  databaseURL: "https://managing-screen-time-default-rtdb.asia-southeast1.firebasedatabase.app",
  storageBucket: "managing-screen-time.appspot.com",
  apiKey: "AIzaSyDNXTbTLRBCkCzxINt-Q-iahnFGkKfBCNM",
  authDomain: "managing-screen-time.firebaseapp.com",
  messagingSenderId: "54296812917",
  measurementId: "G-R79SFB105W",
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
export const functions = getFunctions(app, "us-central1");
