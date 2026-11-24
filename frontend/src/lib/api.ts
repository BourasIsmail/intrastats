/** Erreur renvoyée par l'API (format ProblemDetail : detail + éventuelle liste details). */
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public details: string[] = [],
  ) {
    super(message);
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("X-Requested-With", "intranet"); // exigé par l'API pour toute requête qui modifie des données
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(path, { ...init, headers, credentials: "same-origin" });
  if (!response.ok) {
    let message = `Erreur ${response.status}`;
    let details: string[] = [];
    try {
      const body = await response.json();
      if (typeof body.detail === "string") message = body.detail;
      if (Array.isArray(body.details)) details = body.details;
    } catch {
      /* corps vide ou non JSON */
    }
    throw new ApiError(response.status, message, details);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const post = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });

export const put = <T>(path: string, body: unknown) =>
  api<T>(path, { method: "PUT", body: JSON.stringify(body) });

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.details.length ? `${e.message} : ${e.details.join(" ; ")}` : e.message;
  return e instanceof Error ? e.message : "Erreur inattendue";
}
