import { useEffect } from 'react';

import { useAppDispatch, useAppSelector } from './app/hooks';
import { fetchHello, sendHello } from './features/common/commonSlice';

export default function App() {
  const dispatch = useAppDispatch();
  const { data, status, error } = useAppSelector((state) => state.hello);

  useEffect(() => {
    dispatch(fetchHello());
  }, [dispatch]);

  return (
    <div style={{ padding: 24 }}>
      <h1>End-to-End App</h1>

      <button onClick={() => dispatch(fetchHello())} style={{ marginRight: 8 }}>
        GET
      </button>

      <button onClick={() => dispatch(sendHello({ message: 'Hello from FRONTEND!' }))}>POST</button>

      {status === 'loading' && <p>Loading...</p>}
      {status === 'failed' && <pre>{error}</pre>}
      {status === 'succeeded' && data && (
        <pre style={{ marginTop: 16 }}>{JSON.stringify(data, null, 2)}</pre>
      )}
    </div>
  );
}
