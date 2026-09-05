Linda Java
Design a distributed computer system that should enable distributed processing and mutual remote synchronization of time-consuming jobs. This synchronization should be done in the manner of the virtual common space of tuples common to all jobs within a set of jobs that exists in the C-Linda library.
The program should run in a system consisting of multiple computers connected in a LAN (Local Area Network) or WAN (Wide Area Network).
There are three types of programs in the system:

1.A central server that serves to monitor the execution of distributed processing, store information about available nodes in the network, and allow restarting individual jobs.
2.A workstation that receives jobs to be performed from the central server.
3.A user program that specifies the job to be performed and its parameters.

The process begins when the user program specifies the job to be processed. This job is a program written in the Java programming language packaged in a jar archive. 

After that, the user program contacts the central server to which it forwards the job and the parameters required for its processing. 
When the central server receives the job and its parameters, it forwards it to one workstation, waits for the processing result and returns the complete result to the client.

When the workstation receives the job, it starts executing it, based on the received parameters. The job is executed on the workstation by running the job that is given as a jar archive to which it passes the parameters specified by the client. As a special parameter, it sets the path to the jar archive containing the remote synchronization library. This library is implemented according to the C-Linda library used for synchronization. The interface that needs to be satisfied is given in the appendix to this document.
Since multiple jobs can be started in a given user job (eval(...) method), they should be distributed to free workstations. 

When the program execution is complete, the workstation forwards the collected output data streams to the server, as well as the execution results. To ensure the collection of information about active workstations, the central server checks every x seconds whether a workstation is healthy. If it is not healthy, it informs the user which job has stopped executing. After that, the user should decide whether the job should be terminated or whether the terminated part should be forwarded to a free workstation. If the user is not available, the execution of the entire job is interrupted.
After sending a request for processing, the user program can terminate the connection with the central server. 
The connection can be terminated by shutting down the program or closing the communication channel. 

When it connects the next time, the user program can request the results of the previously specified processing. It should be ensured that the central server can receive a larger number of jobs that need to be processed in parallel. The user program can request information about the status of the job from the central server, and it can also request the results. Immediately after starting, the workstations send information to the central server that they have been started, the characteristics of the platform on which they were started (operating system, Java version) and the number of jobs that they can process in parallel.
The job parameters that the client specifies are: the command that is given to the Java virtual machine to start the archive, the files that need to be transferred from the client computer to the workstation so that the commands can be executed (no more than 6), the files that need to be
transferred from the workstation to the client computer to represent the results (no more than 6). 

These parameters can be specified either via the user interface or via a text file.
The central server logs the time when each job arrived, the number under which the job was saved, the name of the computer to which the job was forwarded, the time when the job was completed and its current status. The status of the job can be: Ready – arrived at the server, but not forwarded to anyone, Scheduled - currently being forwarded to the workstation, Running – execution is in progress, Done – the job was successfully executed, Failed – the job could not be executed, Aborted – the user gave up on executing the job.
Solve the problem using only Java NET network communication. The solution should be independent of the job being performed. For each of these three types of computers, there should be an appropriate graphical user interface (GUI should be developed using Java SWING components or JavaFX). The workstation should be able to run without a user interface.