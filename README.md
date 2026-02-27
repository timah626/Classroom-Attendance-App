# Classroom-Attendance-App


An Android application that automates classroom attendance using Bluetooth technology. The teacher's device acts as a Bluetooth beacon, and students' devices detect it to automatically mark attendance :  no paper, no roll call.



## Features

- **Bluetooth-Based Check-In**: Students are automatically marked present when their device detects the teacher's Bluetooth signal within range.
- **Teacher & Student Dashboards**:Separate login screens and views for teachers and students.
- **Live Attendance List** : Teachers can see in real time who has checked in and who is absent.
- **Time Window**: Attendance can only be registered within a set time window at the start of class. Students who check in after the window are marked late.
- **Attendance History**: View past attendance records filtered by date or by student.
- **Export Reports** : Teachers can export attendance records as a CSV or PDF file.
- *other features will be added with time*


## Sensors Used

- **Bluetooth**: Core feature for detecting student presence in the classroom.


## Tech Stack

- Kotlin (Android)
- Android Bluetooth API
- Firebase (Authentication + Realtime Database)


## Future Improvements

- Camera verification (selfie check-in to prevent proxy attendance)
- GPS double-verification
- Push notifications for absent students



## Project Status

 In development — student project
