const tools = [];
const params = {
    messages: [{ role: "user", content: "Hello!" }],
    tools: tools.length > 0 ? tools : undefined,
    max_tokens: 1024,
    temperature: 0.7,
};
console.log(params);
