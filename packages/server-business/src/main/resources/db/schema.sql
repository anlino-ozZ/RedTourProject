/*
SQLyog Ultimate v11.25 (64 bit)
MySQL - 8.0.26 : Database - s
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`s` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `s`;

/*Table structure for table `course` */

DROP TABLE IF EXISTS `course`;

CREATE TABLE `course` (
  `Cno` char(3) NOT NULL,
  `Cname` varchar(20) NOT NULL,
  `Cpno` char(3) DEFAULT NULL,
  `Credit` tinyint NOT NULL,
  PRIMARY KEY (`Cno`),
  UNIQUE KEY `Cname_union_index` (`Cname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*Data for the table `course` */

insert  into `course`(`Cno`,`Cname`,`Cpno`,`Credit`) values ('C01','英语',NULL,4),('C02','数据结构','C05',2),('C03','数据库','C02',2),('C04','DB_设计','C03',3),('C05','C++',NULL,3),('C06','网络原理','C07',3),('C07','操作系统','C05',3);

/*Table structure for table `sc` */

DROP TABLE IF EXISTS `sc`;

CREATE TABLE `sc` (
  `Sno` char(3) NOT NULL,
  `Cno` char(3) NOT NULL,
  `Grade` tinyint DEFAULT NULL,
  PRIMARY KEY (`Sno`,`Cno`),
  KEY `Cno` (`Cno`),
  KEY `Sno_Cno_index` (`Sno`,`Cno` DESC),
  CONSTRAINT `sc_ibfk_1` FOREIGN KEY (`Sno`) REFERENCES `student` (`Sno`),
  CONSTRAINT `sc_ibfk_2` FOREIGN KEY (`Cno`) REFERENCES `course` (`Cno`),
  CONSTRAINT `sc_chk_1` CHECK ((`Grade` between 0 and 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*Data for the table `sc` */

insert  into `sc`(`Sno`,`Cno`,`Grade`) values ('S01','C01',92),('S01','C03',84),('S02','C01',90),('S02','C02',94),('S02','C03',82),('S03','C01',72),('S03','C02',90),('S04','C03',75);

/*Table structure for table `student` */

DROP TABLE IF EXISTS `student`;

CREATE TABLE `student` (
  `Sno` char(3) NOT NULL,
  `Sname` varchar(10) NOT NULL,
  `Ssex` enum('男','女') NOT NULL DEFAULT '男',
  `Sage` tinyint NOT NULL,
  `Sdept` varchar(10) NOT NULL,
  `Adesss` varchar(10) NOT NULL DEFAULT '未知',
  PRIMARY KEY (`Sno`),
  FULLTEXT KEY `Adesss_fulltext` (`Adesss`),
  CONSTRAINT `student_chk_1` CHECK ((`Sage` between 15 and 30))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*Data for the table `student` */

insert  into `student`(`Sno`,`Sname`,`Ssex`,`Sage`,`Sdept`,`Adesss`) values ('S01','王建平','男',21,'自动化','广州'),('S02','刘华','女',19,'自动化','陕西'),('S03','范林军','女',18,'计算机','上海'),('S04','李伟','男',19,'数学','深圳'),('S05','黄海','男',18,'数学','四川'),('S06','长江','男',20,'数学','广州');

/*Table structure for table `teacher1` */

DROP TABLE IF EXISTS `teacher1`;

CREATE TABLE `teacher1` (
  `teacherno` char(6) NOT NULL COMMENT '教师编号',
  `tname` char(8) NOT NULL COMMENT '教师姓名',
  `major` char(10) NOT NULL COMMENT '专业',
  `prof` char(10) NOT NULL COMMENT '职称',
  `department` char(16) NOT NULL COMMENT '部门',
  `phone` char(11) NOT NULL COMMENT '电话号码',
  PRIMARY KEY (`teacherno`),
  UNIQUE KEY `tname_index` (`tname`),
  KEY `dep_index` (`department`(5)),
  KEY `tname_prof_index` (`tname`,`prof`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*Data for the table `teacher1` */

/*Table structure for table `c_student` */

DROP TABLE IF EXISTS `c_student`;

/*!50001 DROP VIEW IF EXISTS `c_student` */;
/*!50001 DROP TABLE IF EXISTS `c_student` */;

/*!50001 CREATE TABLE  `c_student`(
 `Sno` char(3) ,
 `Sname` varchar(10) ,
 `Sage` tinyint ,
 `Sdept` varchar(10) 
)*/;

/*Table structure for table `s_avgage` */

DROP TABLE IF EXISTS `s_avgage`;

/*!50001 DROP VIEW IF EXISTS `s_avgage` */;
/*!50001 DROP TABLE IF EXISTS `s_avgage` */;

/*!50001 CREATE TABLE  `s_avgage`(
 `SSEX` enum('男','女') ,
 `AVG_AGE` decimal(7,4) 
)*/;

/*Table structure for table `student_birth` */

DROP TABLE IF EXISTS `student_birth`;

/*!50001 DROP VIEW IF EXISTS `student_birth` */;
/*!50001 DROP TABLE IF EXISTS `student_birth` */;

/*!50001 CREATE TABLE  `student_birth`(
 `Sno` char(3) ,
 `Sname` varchar(10) ,
 `Sbirth` int 
)*/;

/*Table structure for table `student_cr` */

DROP TABLE IF EXISTS `student_cr`;

/*!50001 DROP VIEW IF EXISTS `student_cr` */;
/*!50001 DROP TABLE IF EXISTS `student_cr` */;

/*!50001 CREATE TABLE  `student_cr`(
 `Sno` char(3) ,
 `Sname` varchar(10) ,
 `Cname` varchar(20) ,
 `Grade` tinyint 
)*/;

/*Table structure for table `v_course` */

DROP TABLE IF EXISTS `v_course`;

/*!50001 DROP VIEW IF EXISTS `v_course` */;
/*!50001 DROP TABLE IF EXISTS `v_course` */;

/*!50001 CREATE TABLE  `v_course`(
 `Cno` char(3) ,
 `Cname` varchar(20) ,
 `Cpno` char(3) ,
 `Credit` tinyint 
)*/;

/*Table structure for table `v_f_grade` */

DROP TABLE IF EXISTS `v_f_grade`;

/*!50001 DROP VIEW IF EXISTS `v_f_grade` */;
/*!50001 DROP TABLE IF EXISTS `v_f_grade` */;

/*!50001 CREATE TABLE  `v_f_grade`(
 `Sno` char(3) ,
 `Sname` varchar(10) ,
 `Cno` char(3) ,
 `Grade` tinyint 
)*/;

/*View structure for view c_student */

/*!50001 DROP TABLE IF EXISTS `c_student` */;
/*!50001 DROP VIEW IF EXISTS `c_student` */;

/*!50001 CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW `c_student` (`Sno`,`Sname`,`Sage`,`Sdept`) AS select `student`.`Sno` AS `Sno`,`student`.`Sname` AS `Sname`,`student`.`Sage` AS `Sage`,`student`.`Sdept` AS `Sdept` from `student` where (`student`.`Sdept` = '数学') WITH CASCADED CHECK OPTION */;

/*View structure for view s_avgage */

/*!50001 DROP TABLE IF EXISTS `s_avgage` */;
/*!50001 DROP VIEW IF EXISTS `s_avgage` */;

/*!50001 CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW `s_avgage` (`SSEX`,`AVG_AGE`) AS select `student`.`Ssex` AS `Ssex`,avg(`student`.`Sage`) AS `AVG_AGE` from `student` group by `student`.`Ssex` */;

/*View structure for view student_birth */

/*!50001 DROP TABLE IF EXISTS `student_birth` */;
/*!50001 DROP VIEW IF EXISTS `student_birth` */;

/*!50001 CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW `student_birth` (`Sno`,`Sname`,`Sbirth`) AS select `student`.`Sno` AS `Sno`,`student`.`Sname` AS `Sname`,(year(now()) - `student`.`Sage`) AS `Sbirth` from `student` */;

/*View structure for view student_cr */

/*!50001 DROP TABLE IF EXISTS `student_cr` */;
/*!50001 DROP VIEW IF EXISTS `student_cr` */;

/*!50001 CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW `student_cr` (`Sno`,`Sname`,`Cname`,`Grade`) AS select `s`.`Sno` AS `Sno`,`s`.`Sname` AS `Sname`,`c`.`Cname` AS `Cname`,`sc`.`Grade` AS `Grade` from ((`student` `s` join `sc` on((`s`.`Sno` = `sc`.`Sno`))) join `course` `c` on((`sc`.`Cno` = `c`.`Cno`))) */;

/*View structure for view v_course */

/*!50001 DROP TABLE IF EXISTS `v_course` */;
/*!50001 DROP VIEW IF EXISTS `v_course` */;

/*!50001 CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW `v_course` AS select `course`.`Cno` AS `Cno`,`course`.`Cname` AS `Cname`,`course`.`Cpno` AS `Cpno`,`course`.`Credit` AS `Credit` from `course` where (`course`.`Cpno` is null) */;

/*View structure for view v_f_grade */

/*!50001 DROP TABLE IF EXISTS `v_f_grade` */;
/*!50001 DROP VIEW IF EXISTS `v_f_grade` */;

/*!50001 CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW `v_f_grade` (`Sno`,`Sname`,`Cno`,`Grade`) AS select `s`.`Sno` AS `Sno`,`s`.`Sname` AS `Sname`,`sc`.`Cno` AS `Cno`,`sc`.`Grade` AS `Grade` from (`student` `s` join `sc` on((`s`.`Sno` = `sc`.`Sno`))) where ((`s`.`Ssex` = '女') and (`sc`.`Grade` > 90)) */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
