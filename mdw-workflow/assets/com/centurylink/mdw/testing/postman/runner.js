'use strict';

// Runs multiple test items, each with their own case.
var testCase = null;
try {
  //const limberest = require('limberest');
  const limberest = require('../../../../../../../../limberest-js/index.js');
  const path = require('path');
  
  testCase = getTestCase();
  
  var testLoc = path.dirname(testCase.file);
  
  var options = {
    location: testLoc,
    resultLocation: testCase.resultDir,
    logLocation: testCase.resultDir,
    responseHeaders: ['content-type', 'mdw-request-id']
  };

  if (testCase.items) {
    testCase.items.forEach(item => {
      var opts = Object.assign({}, options, item.options);
      if (opts.debug)
        console.log("options: " + JSON.stringify(opts, null, 2));
      // deep clone for values
      var vals = {};
      if (opts.env) {
        vals = Object.assign({}, limberest.env(opts.env), item.values);
      }
      else if (require('fs').existsSync(testLoc + '/localhost.env')) {
        vals = Object.assign({}, limberest.env(testLoc + '/localhost.env'), item.values);
      }
      if (opts.debug)
        console.log("values: " + JSON.stringify(vals, null, 2));
      if (opts.caseName && opts.verify) {
        // verify test results only
        opts.retainResult = opts.retainLog = true;
        opts.overwriteExpected = false;
        var theCase = new (limberest.Case)(opts.caseName, opts);
        setTestResult(null, theCase.verify(vals));
      }
      else {
        // execute test case 
        var group = limberest.group(testCase.file);
        var test = group.test(item.method, item.name);
        var result = { start: new Date().toISOString() };
        test.run(opts, vals, (response, error) => {
          var itemId = item.method + ':' + item.name;
          setTestResponse(itemId, response);
          if (!opts.caseName) {
            result = Object.assign(result, test.verify(vals));
            result.end = new Date().toISOString();
            setTestResult(itemId, result);
          }
        });
      }
    });
  }
}
catch (err) {
  // if not caught, VM can crash
  if (err.stack)
    console.error(err.stack);
  else
    console.error(err);
  try {
    // try to log to file and set status
    if (testCase && testCase.logger) {
      testCase.logger.error(err);
      testCase.logger.error(err.stack);
    }
    setTestResult(null, {status: 'Errored', message: err.toString()});  
  }
  catch (e) {
    console.error(err);
    console.error(err.stack);
  }
}

//prevent unhandled errors from crashing the VM
process.on('unhandledRejection', (err) => {
  console.error('Unhandled Rejection', err);
  if (err.stack)
    console.error(err.stack);
});
process.on('uncaughtException', (err) => {
  console.error('Uncaught Exception', err);
  if (err.stack)
    console.error(err.stack);
});

