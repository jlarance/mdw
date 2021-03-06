/*
 * Copyright (C) 2017 CenturyLink, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.centurylink.mdw.script;

import com.centurylink.mdw.common.MdwException;

public class ExecutionException extends MdwException {

    private static final long serialVersionUID = 1L;

    public ExecutionException(String message) {
        super(message);
    }
    
    public ExecutionException(String message, Throwable t){
        super(-1, message, t);
    }
    
    public ExecutionException(int code, String message, Throwable t){
        super(code, message, t);
    }
}
