const dbUrl = "https://script.google.com/macros/s/AKfycby5t9-U1tB-BfB4E0dM8B3yO9Z2M4x-H6k7O1R8w4m1tJ2F_K7s6p9N0_k3A/exec";
async function run() {
  const res = await fetch(`${dbUrl}?page=1&pageSize=50`);
  console.log(res.status);
  console.log(await res.text());
}
run();
