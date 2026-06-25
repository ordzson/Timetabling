export type Role = 'SUPERADMIN' | 'ADMIN' | 'TEACHER' | 'STUDENT';

export type User = {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
};

export type Session = {
  accessToken: string;
  user: User;
};
