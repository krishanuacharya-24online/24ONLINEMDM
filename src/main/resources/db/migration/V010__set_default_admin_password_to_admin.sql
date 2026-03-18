UPDATE auth_user
SET password_hash = 'c7ad44cbad762a5da0a452f9e854fdc1e0e7a52a38015f23f3eab1d80b931dd472634dfac71cd34ebc35d16ab7fb8a90c81f975113d6c7538dc69dd8de9077ec',
    modified_at = now(),
    modified_by = 'system'
WHERE username = 'admin'
  AND password_hash IN (
      '$2a$10$7EqJtq98hPqEX7fNZaFWoO5lgOG/eo8pLG3dqQ.C1D7tY8F/5F86.',
      'b109f3bbbc244eb82441917ed06d618b9008dd09b3befd1b5e07394c706a8bb980b1d7785e5976ec049b46df5f1326af5a2ea6d103fd07c95385ffab0cacbc86'
  );
