/**
 * A file's size for Settings: MB with one decimal from 0.1 MB, and KB below that, where 0.0 MB would read as empty (a
 * 50 KB typing log once showed 0.0 MB and misled the owner).
 */
export const fileSize = (bytes: number) =>
  bytes < 0.1 * 1_048_576 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1_048_576).toFixed(1)} MB`;
