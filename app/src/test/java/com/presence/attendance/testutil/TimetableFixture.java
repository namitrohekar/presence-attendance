package com.presence.attendance.testutil;

/** The first-year MCA timetable for 2026-27, as transcribed from the department's sheet. */
public final class TimetableFixture {

    /** 38 classes: lectures, tutorials and four lab groups, with initials and names mixed. */
    public static final String FYMCA =
            """
            day,start,end,code,subject,type,batch,faculty,room
            MON,10:30,12:25,R5MC5016L,Business English Lab,Lab,1,NC,AL-301
            MON,10:30,12:25,R5MC5016L,Business English Lab,Lab,3,NC,AL-301
            MON,10:30,12:25,R5MC5019D,Mini Project,Lab,2,RM,
            MON,10:30,12:25,R5MC5017L,Mobile Computing Lab,Lab,4,AK+GS,
            MON,13:30,14:25,,Accounting and Finance,Lecture,,Dr. Amruta Patil,AL-301
            MON,14:30,15:25,R5MC5016L,Business English,Lecture,,Prof. Nilima,AL-301
            MON,15:30,16:25,R5MC5011S,Management Information System,Lecture,,SJ,AL-301
            MON,16:30,17:25,R5MC5013T,Operating System,Lecture,,Prof. Bhakti,AL-301
            TUE,09:30,10:25,R5MC5014S,Mathematical and Statistical Foundations 1,Tutorial,,Prof. Archana+RM,AL-301
            TUE,10:30,12:25,R5MC5016L,Business English Lab,Lab,2,NC,
            TUE,10:30,12:25,R5MC5016L,Business English Lab,Lab,4,NC,
            TUE,10:30,12:25,R5MC5013P,Operating System Lab,Lab,3,BZ,
            TUE,10:30,12:25,R5MC5018L,Web Technology Lab,Lab,1,AP+GS (Rishab Mishra),
            TUE,13:30,14:25,R5MC5014S,Mathematical and Statistical Foundations 1,Lecture,,Prof. Archana,AL-301
            TUE,14:30,15:25,R5MC5012T,Software Engineering & Project Management,Lecture,,Prof. Nikhil,AL-301
            TUE,15:30,16:25,R5MC5013T,Operating System,Lecture,,Prof. Bhakti,AL-301
            WED,09:30,10:25,R5MC5011S,Management Information System,Lecture,,SJ,AL-301
            WED,10:30,12:25,R5MC5013P,Operating System Lab,Lab,2,GS,
            WED,10:30,12:25,R5MC5019D,Mini Project,Lab,3,RM,
            WED,10:30,12:25,R5MC5018L,Web Technology Lab,Lab,4,AP+SJ,
            WED,10:30,12:25,R5MC5017L,Mobile Computing Lab,Lab,1,AK,
            WED,13:30,14:25,R5MC5012T,Software Engineering & Project Management,Lecture,,Prof. Nikhil,AL-301
            THU,09:30,10:25,R5MC5011S,Management Information System,Lecture,,SJ,AL-301
            THU,10:30,11:25,R5MC5014S,Mathematical and Statistical Foundations 1,Tutorial,,Prof. Archana+RM,AL-301
            THU,11:30,12:25,R5MC5014S,Mathematical and Statistical Foundations 1,Lecture,,Prof. Archana,AL-301
            THU,13:30,14:25,R5MC5013T,Operating System,Lecture,,Prof. Bhakti,AL-301
            THU,14:30,16:25,R5MC5013P,Operating System Lab,Lab,1,GS,
            THU,14:30,16:25,R5MC5019D,Mini Project,Lab,4,RM,
            THU,14:30,16:25,R5MC5018L,Web Technology Lab,Lab,3,AP+SJ,
            THU,14:30,16:25,R5MC5017L,Mobile Computing Lab,Lab,2,AK,
            FRI,09:30,10:25,R5MC5012T,Software Engineering & Project Management,Lecture,,Prof. Nikhil,AL-301
            FRI,10:30,12:25,R5MC5013P,Operating System Lab,Lab,4,BZ,
            FRI,10:30,12:25,R5MC5019D,Mini Project,Lab,1,RM,
            FRI,10:30,12:25,R5MC5018L,Web Technology Lab,Lab,2,AP+SJ,
            FRI,10:30,12:25,R5MC5017L,Mobile Computing Lab,Lab,3,GS,
            FRI,13:30,14:25,,Accounting and Finance,Lecture,,Dr. Amruta Patil,AL-301
            FRI,14:30,15:25,,Accounting and Finance,Lecture,,Dr. Amruta Patil,AL-301
            FRI,15:30,16:25,R5MC5014S,Mathematical and Statistical Foundations 1,Lecture,,Prof. Archana,AL-301
            """;

    private TimetableFixture() {}
}
