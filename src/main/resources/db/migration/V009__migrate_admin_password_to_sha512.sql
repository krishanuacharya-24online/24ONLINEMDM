UPDATE auth_user
SET password_hash = 'b109f3bbbc244eb82441917ed06d618b9008dd09b3befd1b5e07394c706a8bb980b1d7785e5976ec049b46df5f1326af5a2ea6d103fd07c95385ffab0cacbc86',
    modified_at = now(),
    modified_by = 'system'
WHERE username = 'admin'
  AND password_hash = '$2a$10$7EqJtq98hPqEX7fNZaFWoO5lgOG/eo8pLG3dqQ.C1D7tY8F/5F86.';
