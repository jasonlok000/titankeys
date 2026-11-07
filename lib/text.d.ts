// Bun bundles imported text files as strings.
declare module "*.txt" {
  const text: string;
  export default text;
}
