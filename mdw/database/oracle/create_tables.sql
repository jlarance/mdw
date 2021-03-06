spool create_tables.lst;

-- general rules for ID length: 
--    definition 8
--    proc instance 16
--    act/trans/var/doc instance 20
--    status 2
--    var type 4
--    owner type, user name: 30
--    activity/process name: 80

CREATE TABLE WORK_TRANSITION_INSTANCE
(
  WORK_TRANS_INST_ID  NUMBER(20)                NOT NULL,
  WORK_TRANS_ID       NUMBER(8)                NOT NULL,
  PROCESS_INST_ID     NUMBER(16)                NOT NULL,
  STATUS_CD           NUMBER(2)                NOT NULL,
  START_DT            DATE,
  END_DT              DATE,
  CREATE_DT           DATE                      DEFAULT SYSDATE               NOT NULL,
  CREATE_USR          VARCHAR2(30 BYTE)         DEFAULT USER                  NOT NULL,
  MOD_DT              DATE,
  MOD_USR             VARCHAR2(30 BYTE),
  COMMENTS            VARCHAR2(1000 BYTE),
  DEST_INST_ID        NUMBER(20)
);

CREATE TABLE USER_GROUP_MAPPING
(
  USER_GROUP_MAPPING_ID  NUMBER(8)             NOT NULL,
  USER_INFO_ID           NUMBER(8)             NOT NULL,
  USER_GROUP_ID          NUMBER(8)             NOT NULL,
  CREATE_DT              DATE                   DEFAULT SYSDATE               NOT NULL,
  CREATE_USR             VARCHAR2(30 BYTE)      DEFAULT USER                  NOT NULL,
  MOD_DT                 DATE,
  MOD_USR                VARCHAR2(30 BYTE),
  COMMENTS               VARCHAR2(1000 BYTE)
);
CREATE TABLE USER_ROLE
(
  USER_ROLE_ID    NUMBER(8)                    NOT NULL,
  USER_ROLE_NAME  VARCHAR2(80 BYTE)           NOT NULL,
  CREATE_DT       DATE                          DEFAULT SYSDATE               NOT NULL,
  CREATE_USR      VARCHAR2(30 BYTE)             DEFAULT USER                  NOT NULL,
  MOD_DT          DATE,
  MOD_USR         VARCHAR2(30 BYTE),
  COMMENTS        VARCHAR2(1000 BYTE)
);
CREATE TABLE USER_INFO
(
  USER_INFO_ID  NUMBER(8)                      NOT NULL,
  CUID          VARCHAR2(30 BYTE)               NOT NULL,
  CREATE_DT     DATE                            DEFAULT SYSDATE               NOT NULL,
  CREATE_USR    VARCHAR2(30 BYTE)               DEFAULT USER                  NOT NULL,
  MOD_DT        DATE,
  MOD_USR       VARCHAR2(30 BYTE),
  END_DATE    DATE,
  NAME      VARCHAR2(30),
  COMMENTS      VARCHAR2(1000 BYTE)
);
CREATE TABLE TASK_INSTANCE
(
  TASK_INSTANCE_ID              NUMBER(20)      NOT NULL,
  TASK_ID                       NUMBER(16)      NOT NULL,
  TASK_INSTANCE_STATUS          NUMBER(2)      NOT NULL,
  TASK_INSTANCE_OWNER           VARCHAR2(30 BYTE) NOT NULL,
  TASK_INSTANCE_OWNER_ID        NUMBER(20)      NOT NULL,
  TASK_CLAIM_USER_ID            NUMBER(8),
  CREATE_DT                     DATE            DEFAULT SYSDATE               NOT NULL,
  CREATE_USR                    VARCHAR2(30 BYTE) DEFAULT USER NOT NULL,
  MOD_DT                        DATE,
  MOD_USR                       VARCHAR2(30 BYTE),
  COMMENTS                      VARCHAR2(1000 BYTE),
  TASK_START_DT                 DATE,
  TASK_END_DT                   DATE,
  TASK_INSTANCE_STATE           NUMBER(1)      DEFAULT 1                     NOT NULL,
  TASK_INSTANCE_REFERRED_AS     VARCHAR2(500 BYTE),
  TASK_INST_SECONDARY_OWNER     VARCHAR2(30 BYTE),
  TASK_INST_SECONDARY_OWNER_ID  NUMBER(20),
  DUE_DATE                      DATE,
  PRIORITY                      NUMBER(3),
  MASTER_REQUEST_ID             VARCHAR2(128 BYTE),
  TASK_TITLE                    VARCHAR2(512)
);

CREATE TABLE VARIABLE_INSTANCE
(
  VARIABLE_INST_ID  NUMBER(20)                  NOT NULL,
  VARIABLE_ID       NUMBER(8)                  NOT NULL,
  PROCESS_INST_ID   NUMBER(16)                  NOT NULL,
  CREATE_DT         DATE                        DEFAULT SYSDATE               NOT NULL,
  CREATE_USR        VARCHAR2(30 BYTE)           DEFAULT USER                  NOT NULL,
  MOD_DT            DATE,
  MOD_USR           VARCHAR2(30 BYTE),
  COMMENTS          VARCHAR2(1000 BYTE),
  VARIABLE_VALUE    VARCHAR2(4000 BYTE),
  VARIABLE_NAME   VARCHAR2(80 BYTE),
  VARIABLE_TYPE_ID  NUMBER(4)
);

CREATE TABLE USER_GROUP
(
  USER_GROUP_ID  NUMBER(8)                     NOT NULL,
  GROUP_NAME     VARCHAR2(80 BYTE)              NOT NULL,
  CREATE_DT      DATE                           DEFAULT SYSDATE               NOT NULL,
  CREATE_USR     VARCHAR2(30 BYTE)              DEFAULT USER                  NOT NULL,
  MOD_DT         DATE,
  MOD_USR        VARCHAR2(30 BYTE),
  END_DATE     DATE,
  PARENT_GROUP_ID NUMBER(8),
  COMMENTS       VARCHAR2(1000 BYTE)
);

CREATE TABLE USER_ROLE_MAPPING
(
  USER_ROLE_MAPPING_ID        NUMBER(8)        NOT NULL,
  USER_ROLE_MAPPING_OWNER     VARCHAR2(16 BYTE) NOT NULL,
  USER_ROLE_MAPPING_OWNER_ID  NUMBER(8)        NOT NULL,
  CREATE_DT                   DATE              DEFAULT SYSDATE               NOT NULL,
  CREATE_USR                  VARCHAR2(30 BYTE) DEFAULT USER NOT NULL,
  MOD_DT                      DATE,
  MOD_USR                     VARCHAR2(30 BYTE),
  COMMENTS                    VARCHAR2(1000 BYTE),
  USER_ROLE_ID                NUMBER(8)        NOT NULL
);
CREATE TABLE ATTACHMENT
(
  ATTACHMENT_ID            NUMBER(20),
  ATTACHMENT_OWNER         VARCHAR2(30 BYTE)  NOT NULL,
  ATTACHMENT_OWNER_ID      NUMBER(20)           NOT NULL,
  ATTACHMENT_NAME          VARCHAR2(1000 BYTE)  NOT NULL,
  ATTACHMENT_LOCATION      VARCHAR2(1000 BYTE),
  ATTACHMENT_STATUS        NUMBER(1)           NOT NULL,
  CREATE_DT                DATE                 DEFAULT SYSDATE               NOT NULL,
  CREATE_USR               VARCHAR2(30 BYTE)    DEFAULT USER                  NOT NULL,
  MOD_DT                   DATE,
  MOD_USR                  VARCHAR2(30 BYTE),
  COMMENTS                 VARCHAR2(1000 BYTE),
  ATTACHMENT_CONTENT_TYPE  VARCHAR2(1000 BYTE)  NOT NULL
);
CREATE TABLE ACTIVITY_INSTANCE
(
  ACTIVITY_INSTANCE_ID  NUMBER(20)              NOT NULL,
  ACTIVITY_ID           NUMBER(8)              NOT NULL,
  PROCESS_INSTANCE_ID   NUMBER(16)              NOT NULL,
  STATUS_CD             NUMBER(2)              NOT NULL,
  START_DT              DATE                    DEFAULT SYSDATE               NOT NULL,
  END_DT                DATE,
  CREATE_DT             DATE                    DEFAULT SYSDATE               NOT NULL,
  CREATE_USR            VARCHAR2(30 BYTE)       DEFAULT USER                  NOT NULL,
  MOD_DT                DATE,
  MOD_USR               VARCHAR2(30 BYTE),
  COMMENTS              VARCHAR2(1000 BYTE),
  STATUS_MESSAGE        VARCHAR2(4000 BYTE),
  COMPCODE        VARCHAR2(80 BYTE),  
  ENGINE_ID       VARCHAR(8)
);

CREATE TABLE PROCESS_INSTANCE
(
  PROCESS_INSTANCE_ID  NUMBER(16)               NOT NULL,
  PROCESS_ID           NUMBER(16)               NOT NULL,
  OWNER                VARCHAR2(30 BYTE)      NOT NULL,
  OWNER_ID             NUMBER(16)               NOT NULL,
  SECONDARY_OWNER      VARCHAR2(30 BYTE),
  SECONDARY_OWNER_ID   NUMBER(20),
  STATUS_CD            NUMBER(2)               NOT NULL,
  START_DT             DATE                     DEFAULT SYSDATE               NOT NULL,
  END_DT               DATE,
  CREATE_DT            DATE                     DEFAULT SYSDATE               NOT NULL,
  CREATE_USR           VARCHAR2(30 BYTE)        DEFAULT USER                  NOT NULL,
  MOD_DT               DATE,
  MOD_USR              VARCHAR2(30 BYTE),
  COMMENTS             VARCHAR2(1000 BYTE),
  MASTER_REQUEST_ID    VARCHAR2(80 BYTE),
  COMPCODE         VARCHAR2(80 BYTE)  
);
CREATE TABLE ATTRIBUTE
(
  ATTRIBUTE_ID        NUMBER(8)                NOT NULL,
  ATTRIBUTE_OWNER     VARCHAR2(30 BYTE)       NOT NULL,
  ATTRIBUTE_OWNER_ID  NUMBER(16)                NOT NULL,
  ATTRIBUTE_NAME      VARCHAR2(500 CHAR)       NOT NULL,
  ATTRIBUTE_VALUE     VARCHAR2(4000 BYTE),
  CREATE_DT           DATE                      DEFAULT SYSDATE               NOT NULL,
  CREATE_USR          VARCHAR2(30 BYTE)         DEFAULT USER                  NOT NULL,
  MOD_DT              DATE,
  MOD_USR             VARCHAR2(30 BYTE),
  COMMENTS            VARCHAR2(1000 BYTE)
);

CREATE TABLE EVENT_WAIT_INSTANCE
(
  EVENT_WAIT_INSTANCE_ID        NUMBER(20)      NOT NULL,
  EVENT_NAME                    VARCHAR2(1000 BYTE) NOT NULL,
  EVENT_WAIT_INSTANCE_OWNER_ID  NUMBER(20)      NOT NULL,
  EVENT_WAIT_INSTANCE_OWNER     VARCHAR2(30 BYTE) NOT NULL,
  EVENT_SOURCE                  VARCHAR2(80 BYTE) NOT NULL,
  WORK_TRANS_INSTANCE_ID        NUMBER(20)      NOT NULL,
  WAKE_UP_EVENT                 VARCHAR2(30 BYTE) NOT NULL,
  STATUS_CD                     NUMBER(2)      NOT NULL,
  CREATE_DT                     DATE            DEFAULT SYSDATE               NOT NULL,
  CREATE_USR                    VARCHAR2(30 BYTE) DEFAULT USER NOT NULL,
  MOD_DT                        DATE,
  MOD_USR                       VARCHAR2(30 BYTE),
  COMMENTS                      VARCHAR2(1000 BYTE)
);

CREATE TABLE EVENT_INSTANCE
(
  EVENT_NAME                    VARCHAR(1000) NOT NULL,
  DOCUMENT_ID           NUMBER(20),
  STATUS_CD                     NUMBER(2) NOT NULL,
  CREATE_DT                     DATE DEFAULT SYSDATE NOT NULL,
  CONSUME_DT                    DATE,
  PRESERVE_INTERVAL       NUMBER(10),
  AUXDATA           VARCHAR2(4000),
  REFERENCE           VARCHAR2(1000),
  COMMENTS                      VARCHAR(1000)
);

CREATE TABLE INSTANCE_NOTE
(
  INSTANCE_NOTE_ID        NUMBER(20),
  INSTANCE_NOTE_OWNER_ID  NUMBER(20)            NOT NULL,
  INSTANCE_NOTE_OWNER     VARCHAR2(30 BYTE)    NOT NULL,
  INSTANCE_NOTE_NAME      VARCHAR2(256 BYTE)    NOT NULL,
  INSTANCE_NOTE_DETAILS   VARCHAR2(4000 BYTE),
  CREATE_DT               DATE                  DEFAULT SYSDATE               NOT NULL,
  CREATE_USR              VARCHAR2(30 BYTE)     DEFAULT USER                  NOT NULL,
  MOD_DT                  DATE,
  MOD_USR                 VARCHAR2(30 BYTE),
  COMMENTS                VARCHAR2(1000 BYTE)
);

CREATE TABLE EVENT_LOG
(
  EVENT_LOG_ID        NUMBER(24)                NOT NULL,
  EVENT_NAME          VARCHAR2(1000 BYTE)       NOT NULL,
  EVENT_LOG_OWNER_ID  NUMBER(20)                NOT NULL,
  EVENT_LOG_OWNER     VARCHAR2(30 BYTE)       NOT NULL,
  EVENT_SOURCE        VARCHAR2(80 BYTE)       NOT NULL,
  CREATE_DT           DATE                      DEFAULT SYSDATE               NOT NULL,
  CREATE_USR          VARCHAR2(30 BYTE)         DEFAULT USER                  NOT NULL,
  MOD_DT              DATE,
  MOD_USR             VARCHAR2(30 BYTE),
  COMMENTS            VARCHAR2(1000 BYTE),
  EVENT_CATEGORY      VARCHAR2(80 BYTE)       NOT NULL,
  STATUS_CD           NUMBER(2)                NOT NULL,
  EVENT_SUB_CATEGORY  VARCHAR2(80 BYTE)
);

CREATE TABLE DOCUMENT
(
  DOCUMENT_ID   NUMBER(20)          NOT NULL,
  DOCUMENT_TYPE     VARCHAR2(80 BYTE),
  OWNER_TYPE    VARCHAR2(30 BYTE)     NOT NULL,
  OWNER_ID      NUMBER(20)          NOT NULL,
  CREATE_DT         DATE                        DEFAULT SYSDATE               NOT NULL,
  MODIFY_DT     DATE,
  STATUS_CODE         NUMBER(4),
  STATUS_MESSAGE      VARCHAR2(1000)
);

-- not used when mongodb is present
CREATE TABLE DOCUMENT_CONTENT
(
  DOCUMENT_ID         NUMBER(20),
  CONTENT             CLOB      NOT NULL  
);

CREATE TABLE TASK_INST_GRP_MAPP (
  TASK_INSTANCE_ID      NUMBER(38)  NOT NULL,
  USER_GROUP_ID       NUMBER(38)  NOT NULL,
  CREATE_DT     DATE DEFAULT SYSDATE NOT NULL
);

CREATE TABLE TASK_INST_INDEX (
  TASK_INSTANCE_ID      NUMBER(38)  NOT NULL,
  INDEX_KEY         VARCHAR2(64) NOT NULL,
  INDEX_VALUE       VARCHAR2(256) NOT NULL,
  CREATE_DT     DATE DEFAULT SYSDATE NOT NULL
);

CREATE TABLE SOLUTION
(
  SOLUTION_ID    NUMBER(20)        NOT NULL,
  ID             VARCHAR2(128)     NOT NULL, -- TODO: unique constraint
  NAME           VARCHAR2(1024)    NOT NULL,
  OWNER_TYPE     VARCHAR2(128)     NOT NULL,
  OWNER_ID       VARCHAR2(128)     NOT NULL,  
  CREATE_DT      DATE              DEFAULT SYSDATE,
  CREATE_USR     VARCHAR2(30)      DEFAULT USER,
  MOD_DT         DATE,
  MOD_USR        VARCHAR2(30),
  COMMENTS       VARCHAR2(1024)
);

CREATE TABLE SOLUTION_MAP
(
  SOLUTION_ID    NUMBER(20)        NOT NULL,
  MEMBER_TYPE    VARCHAR2(128)     NOT NULL,
  MEMBER_ID      VARCHAR2(128)     NOT NULL,
  CREATE_DT      DATE              NOT NULL,
  CREATE_USR     VARCHAR(30)       NOT NULL,
  MOD_DT         DATE,
  MOD_USR        VARCHAR2(30),
  COMMENTS       VARCHAR2(1024)
);

CREATE TABLE VALUE
(
  NAME            VARCHAR(1024)    NOT NULL,
  VALUE           VARCHAR(2048)    NOT NULL,
  OWNER_TYPE      VARCHAR(128)     NOT NULL,
  OWNER_ID        VARCHAR(128)     NOT NULL,
  CREATE_DT       DATE        NOT NULL,
  CREATE_USR      VARCHAR(30)      NOT NULL,
  MOD_DT          DATE,
  MOD_USR         VARCHAR(30),
  COMMENTS        VARCHAR(1024)
);

spool off;