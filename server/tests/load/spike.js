import autocannon from 'autocannon';

async function run() {
  console.log('Starting load test (100 concurrent connections for 10s)...');
  
  const result = await autocannon({
    url: 'http://localhost:4000/health',
    connections: 100,
    duration: 10,
    pipelining: 1,
  });

  console.log(autocannon.printResult(result));
  process.exit(0);
}

run().catch(console.error);
